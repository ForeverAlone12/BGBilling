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
        ArrayList<Traffic> traffic = new ArrayList<>();
        String query = "SELECT *"
                + "From traffic"
                + "WHERE status not false" // ???????????
                + "GROUP BY contract_id";
        PreparedStatement ps = con.prepareStatement(query);
        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            int id = rs.getInt("id");
            int contract_id = rs.getInt("contract_id");
            int interzone = rs.getInt("interzone");
            int intercity = rs.getInt("intercity");
            int international = rs.getInt("international");
            Date day = rs.getDate("day");
            boolean status = rs.getBoolean("status");
            traffic.add(new Traffic(id, contract_id, interzone, intercity, international, day, status));
        }

        // вызов функции, которая возвращает звонки в виде 
        // <номер_договора, категория звонка, длительность звонка>
        // входные параметры:
        // 1) с какого  периода произодится выборка
        // 2) по каккой период производится выборка
        // 3) имя таблицы, из которой производится выборка
        ArrayList<Calls> calls = new ArrayList<>();
        query = "CALL getCalls(?,?,?)";
        ps = con.prepareStatement(query);
        rs = ps.executeQuery();

        // 
        try {
            ps = con.prepareStatement(query);

            ps.setTimestamp(1, TimeUtils.convertCalendarToTimestamp(from));
            ps.setTimestamp(2, TimeUtils.convertCalendarToTimestamp(to));
            ps.setString(3, ServerUtils.getModuleMonthTableName(" log_session_", from.getTime(), 0));

            rs = ps.executeQuery();
            while (rs.next()) {
                int contract_id = rs.getInt("contract_id");
                String categories = rs.getString("???"); // ?????????????
                int session_time = rs.getInt("???");
                calls.add(new Calls(contract_id, categories, session_time));
            }
        } catch (SQLException e) {
            print("Не удалось извлечь данные");
            logger.error("Не удалось извлечь данные");
            logger.error(e.getMessage(), e);
        }

        // определение превышения трафика
        for (int i = 0; i < calls.size(); i++) {
            for (int j = 0; j < traffic.size(); j++) {

            }
        }

    }

    /**
     * Приостановление действий контракта
     */
    private static void ContractStop() {

    }

    /**
     * Преобразование числа в строку
     *
     * @param numb число
     * @return число в встроковом представлении
     */
    private static String ToString(int numb) {
        return Integer.toString(numb);
    }

    /**
     * Получение данных из БД
     *
     * @param query запрос
     */
    private ResultSet GetResult(PreparedStatement query) throws SQLException {
        ResultSet rez = query.executeQuery();
        return rez;

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

    /**
     * Определение типа исходящего звонка
     *
     * @param number телефонный номер в формате E.164
     * @return 1 - международный, 2 - междугородний, 3 - внутризоновый
     */
    public static int WhatTheCall(String number, List<Traffic> mobile) {
        int call = 0;

        /* определение масштаба звонка:
            7 - звонок внутри страны, 
            8 - звонок в другие страны.
         */
        String codeCountry = number.substring(0, 1);
        if (!codeCountry.equals("7")) {
            call = 1;
        } else {
            String prefix = number.substring(1, 4);
            String numb = number.substring(4);
            call = prefix.equals(CODE) || isHaveNumber(prefix, numb, mobile) ? 3 : 2;
        }
        return call;

    }

    /**
     * Принадлежит ли сотовый телефон операторам Иркутской области
     *
     * @param code код оператора
     * @param number номер телефона
     * @return true - принадлежит, false - не принадлежит
     */
    public static boolean isHaveNumber(String code, String number, List<Traffic> mobile) {

        int numb = Integer.parseInt(number);

        if (mobile.stream().filter((val) -> (val.getCode().trim().equals(code))).map((val) -> {
            int start = Integer.parseInt(val.getStartNumber().trim());
            int end = Integer.parseInt(val.getEndNumber().trim());
            boolean param2 = (numb >= start && numb <= end);
            return param2;
        }).anyMatch((param2) -> (param2))) {
            return true;
        }
        return false;
    }

}
