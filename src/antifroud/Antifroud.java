package antifroud;

import bitel.billing.common.TimeUtils;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;

import java.util.Date;
import java.util.HashMap;
import ru.bitel.bgbilling.kernel.script.server.dev.GlobalScriptBase;
import ru.bitel.bgbilling.server.util.Setup;
import ru.bitel.common.sql.ConnectionSet;
import org.apache.log4j.Logger;
import ru.bitel.bgbilling.server.util.ServerUtils;

public class Antifroud extends GlobalScriptBase {

    @Override
    public void execute(Setup setup, ConnectionSet connectionSet) throws Exception {

        // обработчик ошибок
        Logger logger = Logger.getLogger(this.getClass());

        // определение текущего времени
        Calendar from = Calendar.getInstance();
        from.set(Calendar.YEAR, 2017);
        from.set(Calendar.MONTH, 7); //месяцы в Calendar нумеруются с 0. Так исторически сложилось :)
        from.set(Calendar.DAY_OF_MONTH, 1);
        from.set(Calendar.HOUR_OF_DAY, 0);
        from.set(Calendar.MINUTE, 0);
        from.set(Calendar.SECOND, 0);

        Calendar to = (Calendar) from.clone();
        to.add(Calendar.HOUR_OF_DAY, 1);

        // подключение к БД
        Connection con = connectionSet.getConnection();

        // считывание данных о звонках за день
        HashMap<Integer, Traffic> traffic = new HashMap<>();
        String query = "SELECT id, contract_id, interzone, international, day, status"
                + "From ?"
                + "WHERE status = false"
                + "GROUP BY contract_id";
        PreparedStatement ps = con.prepareStatement(query);
        ps.setString(0, "traffic");

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            int id = rs.getInt("id");
            int contract_id = rs.getInt("contract_id");
            int interzone = rs.getInt("interzone");
            int intercity = rs.getInt("intercity");
            int international = rs.getInt("international");
            Date day = rs.getDate("day");
            boolean status = rs.getBoolean("status");
            traffic.put(contract_id, new Traffic(id, contract_id, interzone, intercity, international, day, status));
        }

        // вызов функции, которая возвращает звонки в виде 
        // <номер_договора, категория звонка, длительность звонка>
        // входные параметры:
        // 1) с какого  периода произодится выборка
        // 2) по каккой период производится выборка
        // 3) имя таблицы, из которой производится выборка
        ArrayList<Calls> calls = new ArrayList<>();
        query = "SELECT calls.cid, calls.category, sum(calls.round_session_time) as time"
                + "FROM (SELECT "
                + "s.id,"
                + "s.cid,"
                + "s.from_number_164 `from`,"
                + "s.to_number_164 number,"
                + "s.round_session_time,"
                + "IF (s.to_number_164 LIKE '8%', 'Международная',"
                + "IF (SUBSTR(s.to_number_164,1,3)=SUBSTR(s.from_number_164,1,3),'Внутризоновые','Междугородние')"
                + ") category"
                + "FROM log_session_18_201708_fraud s"
                + "WHERE s.session_start BETWEEN ? AND ?"
                + "LIMIT 10000) calls"
                + "GROUP BY calls.cid, calls.category;";
        ps = con.prepareStatement(query);
        rs = ps.executeQuery();

        // 
        try {
            ps = con.prepareStatement(query);

            ps.setTimestamp(1, TimeUtils.convertCalendarToTimestamp(from));
            ps.setTimestamp(2, TimeUtils.convertCalendarToTimestamp(to));
            ps.setString(3, ServerUtils.getModuleMonthTableName(" log_session_", from.getTime(), 18));

            rs = ps.executeQuery();
            while (rs.next()) {
                int contract_id = rs.getInt("contract_id");
                String categories = rs.getString("category");
                int session_time = rs.getInt("time");
                calls.add(new Calls(contract_id, categories, session_time));
            }
        } catch (SQLException e) {
            print("Не удалось извлечь данные");
            logger.error("Не удалось извлечь данные");
            logger.error(e.getMessage(), e);
        }

        // определение превышения трафика
        for (int i = 0; i < calls.size(); i++) {
            Calls call = calls.get(i);

            int contract = call.getContarct_id();

            // 
            query = "Select id, fc"
                    + "FROM contract"
                    + "Where id = ?";

            ps.setInt(1, contract);
            rs = ps.executeQuery();
            // 0- физ. лицо, 1 - юр.лицо
            int typeUser = 0;
            while (rs.next()) {
                typeUser = rs.getInt("fc");
            }

            String typeCall = call.getCategories();
            int timeCall = call.getTime();

            if (traffic.containsKey(call.getContarct_id())) {
                Traffic tr = traffic.get(call.getContarct_id());
                int t = 0;
                switch (typeCall) {
                    case "Внутризоновый":
                        t = tr.getInterzone();
                        break;
                    case "Междугородний":
                        t = tr.getIntercity();
                        break;
                    case "Международный":
                        t = tr.getInternational();
                        break;
                    //default:   throw new Exception("Неопознанный тип звонка") ;
                }

                t += timeCall;
                switch (typeUser) {
                    case 0:
                        if (InZoneNatural(t) || IntercityNatural(t) || International(t)) {
                            query = "update contaract set statuc = 4 WHERE id = ?";
                            ps.setInt(1, contract);
                            rs = ps.executeQuery();
                        }
                        break;
                    case 1:
                        if (InZoneLegal(t) || IntercityLegal(t) || International(t)) {
                            query = "update contaract set statuc = 4 WHERE id = ?";
                            ps.setInt(1, contract);
                            rs = ps.executeQuery();
                        }
                        ;
                        break;
                }

            }

        }

    }

    /**
     * Превышен ли лимит по внутризоновым телефонным соединениям для физических
     * лиц
     *
     * @param session количество выговоренных секунд
     * @return true - лимит превышен, false - лимит не превышен
     */
    private boolean InZoneNatural(int session) {

        // количество доступных минут в день для физических лиц
        int LIMIT_MINUTES = 200;
        int LIMIT_SECONDS = LIMIT_MINUTES * 60;

        boolean rez = (session > LIMIT_SECONDS);

        return rez;
    }

    /**
     * Превышен ли лимит по внутризоновым телефонным соединениям для юридических
     * лиц
     *
     * @param session количество выговоренных секунд
     * @return true - лимит превышен, false - лимит не превышен
     */
    private boolean InZoneLegal(int session) {
        // количество доступных минут в день для юридических лиц
        int LIMIT_MINUTES = 1000;
        int LIMIT_SECONDS = LIMIT_MINUTES * 60;

        boolean rez = (session > LIMIT_SECONDS);

        return rez;
    }

    /**
     * Превышен ли лимит по межгородним телефонным соединениям для физических
     * лиц
     *
     * @param session количество выговоренных секунд
     * @return true - лимит превышен, false - лимит не превышен
     */
    private boolean IntercityNatural(int session) {
        // количество доступных минут в день для физических лиц
        int LIMIT_MINUTES = 200;
        int LIMIT_SECONDS = LIMIT_MINUTES * 60;

        boolean rez = (session > LIMIT_SECONDS);

        return rez;
    }

    /**
     * Превышен ли лимит по межгородним телефонным соединениям для юридических
     * лиц
     *
     * @param session количество выговоренных секунд
     * @return true - лимит превышен, false - лимит не превышен
     */
    private boolean IntercityLegal(int session) {
        // количество доступных минут в день для физических лиц
        int LIMIT_MINUTES = 1000;
        int LIMIT_SECONDS = LIMIT_MINUTES * 60;

        boolean rez = (session > LIMIT_SECONDS);

        return rez;
    }

    /**
     * Превышен ли лимит по международным телефонным соединениям
     *
     * @param session количество выговоренных секунд
     * @return true - лимит превышен, false - лимит не превышен
     */
    private boolean International(int session) {
        // количество доступных минут в день
        int LIMIT_MINUTES = 120;
        int LIMIT_SECONDS = LIMIT_MINUTES * 60;

        boolean rez = (session > LIMIT_SECONDS);

        return rez;
    }

}
