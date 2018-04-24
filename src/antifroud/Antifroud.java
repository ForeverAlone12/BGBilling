package antifroud;

import bitel.billing.common.TimeUtils;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;

import java.util.HashMap;
import ru.bitel.bgbilling.kernel.script.server.dev.GlobalScriptBase;
import ru.bitel.bgbilling.server.util.Setup;
import ru.bitel.common.sql.ConnectionSet;
import org.apache.log4j.Logger;
import ru.bitel.bgbilling.common.BGException;
import ru.bitel.bgbilling.kernel.contract.api.common.bean.Contract;
import ru.bitel.bgbilling.kernel.contract.api.server.bean.ContractDao;
import ru.bitel.bgbilling.server.util.ServerUtils;
import ru.bitel.common.ParameterMap;

public class Antifroud extends GlobalScriptBase {

    private Connection con;
    private ConnectionSet conSet;

    /**
     * Лимит времени по местной связи для абонентов - физических лиц
     */
    private int limitSecondsNaturalZone;

    /**
     * Лимит времени по местной связи для абонентов - юридических лиц
     */
    private int limitSecondsLegalZone;

    /**
     * Лимит времени по междугородней связи для абонентов - физических лиц
     */
    private int limitSecondsNaturalIntercity;

    /**
     * Лимит времени по междугородней связи для абонентов - юридических лиц
     */
    private int limitSecondsLegalIntercity;

    /**
     * Лимит времени по международной связи
     */
    private int limitSecondsInternational;

    @Override
    public void execute(Setup setup, ConnectionSet connectionSet) throws Exception {

        // обработчик ошибок
        Logger logger = Logger.getLogger(this.getClass());

        conSet = connectionSet;

        ParameterMap setting = setup.sub("fraud");
        limitSecondsNaturalZone = setting.getInt("LIMIT_SECONDS_NATURAL_ZONE", 1200);
        limitSecondsLegalZone = setting.getInt("LIMIT_SECONDS_LEGAL_ZONE", 60000);
        limitSecondsNaturalIntercity = setting.getInt("LIMIT_SECONDS_NATURAL_INTERCITY", 1200);
        limitSecondsLegalIntercity = setting.getInt("LIMIT_SECONDS_LEGAL_INTERCITY", 60000);
        limitSecondsInternational = setting.getInt("LIMIT_SECONDS_INTERNATIONAL", 1200);

        // определение текущего времени
        Calendar from = Calendar.getInstance();

        // --- удалить на боевой версии
        from.set(Calendar.YEAR, 2017);
        //месяцы в Calendar нумеруются с 0. Так исторически сложилось :)
        from.set(Calendar.MONTH, 7);
        from.set(Calendar.DAY_OF_MONTH, 1);
        from.set(Calendar.HOUR_OF_DAY, 0);
        from.set(Calendar.MINUTE, 0);
        from.set(Calendar.SECOND, 0);
        // --- конец удаления    

        Calendar to = (Calendar) from.clone();
        to.add(Calendar.HOUR_OF_DAY, 1);

        print("Начало выборки = " + from.getTime());
        print("Конец выборки = " + to.getTime());
        // Попытка подключения к БД
        try {
            con = connectionSet.getConnection();
        } catch (Exception ex) {
            logger.error("Не удалось подключиться к БД\n");
            logger.error(ex.getMessage(), ex);
            throw new BGException("Ошибка подключения к БД в скрипте Antifroud");
        }

        // считывание данных об обработанных звонках за день
        HashMap<Integer, Traffic> traffic = new HashMap<>();
        try {
            traffic = getTraffic();
        } catch (SQLException ex) {
            logger.error("Не удалось извлечь данные об обработанных звонках за день.\n"
                    + "Время начала извлечения " + from.toString());
            logger.error(ex.getMessage(), ex);
            throw new BGException("Не удалось извлечь данные об обработанных звонках за день.\n"
                    + "Время начала извлечения " + from.toString());
        }

        // Считывание необработанных звонков
        ArrayList<Calls> calls = new ArrayList<>();
        try {
            calls = getCalls(from, to);
        } catch (SQLException e) {
            logger.error("Не удалось извлечь данные о звонках с "
                    + from.toString() + " по " + to.toString() + "\n");
            logger.error(e.getMessage(), e);
            throw new BGException("Не удалось извлечь данные о звонках с "
                    + from.toString() + " по " + to.toString() + "\n");
        }

        print("Количество считанных звонков = " + calls.size());
        print("Количество данных трафика = " + traffic.size());
        
        Calls call; // данные звонка абонента
        Traffic tr; // текущий трафик абонента
        // информация о пользователях, которых нельзя блокировать
        ArrayList<Users> users = new ArrayList<>();
        try {
            users = getUsers();
        } catch (SQLException ex) {
            logger.error("Не удалось извлечь данные  пользователях, которых нельзя блокировать\n");
            logger.error(ex.getMessage(), ex);
            throw new BGException("Не удалось извлечь данные  пользователях, которых нельзя блокировать\n");
        }

        int typeUser = 0; // тип пользователя: 0 - физ. лицо, 1 - юр.лицо
        // определение превышения трафика
        for (int i = 0; i < calls.size(); i++) {

            call = calls.get(i);

            try {
                typeUser = WhatUser(call.getContarct_id());
            } catch (SQLException e) {
                logger.error("Не удалось извлечь данные о пользователе c id = " + call.getContarct_id() + "\n");
                logger.error(e.getMessage(), e);
                throw new BGException("Не удалось извлечь данные о пользователе c id = " + call.getContarct_id() + "\n");
            }
            tr = traffic.getOrDefault(call.getContarct_id(), new Traffic(call.getContarct_id()));

            // если есть данные о звонках
            if (traffic.containsKey(call.getContarct_id())) {

                try {
                    switch (call.getCategories()) {
                        case "1":
                            tr.setInternational(tr.getInternational() + calls.get(i).getTime());
                            break;
                        case "2":
                            tr.setIntercity(tr.getIntercity() + calls.get(i).getTime());
                            break;
                        case "3":
                            tr.setInterzone(tr.getInterzone() + calls.get(i).getTime());
                            break;
                        default:
                            throw new Exception("Неопознанный тип звонка");
                    }
                } catch (Exception ex) {
                    logger.error("Не удалось распознать тип звонка. Полученный тип: " + call.getCategories() + "\n");
                    logger.error(ex.getMessage(), ex);
                    throw new BGException("Не удалось распознать тип звонка. Полученный тип: " + call.getCategories() + "\n");
                }

                if (!users.contains(call.getContarct_id())) {
                    try {
                        switch (typeUser) {
                            case 0:
                                if (InZoneNatural(tr.getInterzone()) || IntercityNatural(tr.getIntercity()) || International(tr.getInternational())) {
                                    LockContract(call.getContarct_id());
                                    tr.setStatus((byte) 0);
                                }
                                break;
                            case 1:
                                if (InZoneLegal(tr.getInterzone()) || IntercityLegal(tr.getIntercity()) || International(tr.getInternational())) {
                                    LockContract(call.getContarct_id());
                                    tr.setStatus((byte) 0);
                                }
                                break;
                            default:
                                throw new Exception("Неопознанный тип абонента");
                        }
                    } catch (Exception ex) {
                        logger.error("Не удалось распознать абонента. Номер контракта: " + call.getContarct_id() + ". Полученный тип абонента: " + typeUser);
                        logger.error(ex.getMessage(), ex);
                        throw new BGException("Не удалось распознать абонента. Номер контракта: " + call.getContarct_id() + ". Полученный тип абонента: " + typeUser);
                    }

                    try {
                        AddDataInTraffic(tr, from);
                    } catch (SQLException ex) {
                        logger.error("Ошибка вставки данных о звонках абонента: " + call.getContarct_id());
                        logger.error(ex.getMessage(), ex);
                        throw new BGException("Ошибка вставки данных о звонках абонента: " + call.getContarct_id());
                    }

                }// if (users.contain...)
            } else { // информации о звонках пользователя нет
                try {
                    AddDataInTraffic(tr, from);
                } catch (SQLException ex) {
                    logger.error("Ошибка вставки данных о звонках абонента: " + call.getContarct_id());
                    logger.error(ex.getMessage(), ex);
                    throw new BGException("Ошибка вставки данных о звонках абонента: " + call.getContarct_id());
                }

            } // if (trafic.contain...
        }// for
    }

    /**
     * Получение данных о трафике абонентов
     *
     * @return
     * @throws SQLException
     */
    private HashMap<Integer, Traffic> getTraffic() throws SQLException {
        HashMap<Integer, Traffic> traffic = new HashMap<>();
        String query = "SELECT id, contract_id, interzone, intercity, international, day, status \n"
                + "FROM traffic \n"
                + "WHERE status = false \n"
                + "GROUP BY contract_id";
        PreparedStatement ps = con.prepareStatement(query);
        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            traffic.put(rs.getInt("contract_id"), new Traffic(rs.getInt("id"),
                    rs.getInt("contract_id"),
                    rs.getInt("interzone"),
                    rs.getInt("intercity"),
                    rs.getInt("international"),
                    rs.getDate("day"),
                    rs.getByte("status")));
        }
        rs.close();

        return traffic;
    }

    /**
     * Выбор данных о звонках
     *
     * @param from дата начало выборки
     * @param to дата конца выборки
     * @return данные о звонках в виде "номер_договора, категория звонка,
     * длительность звонка"
     * @throws SQLException
     */
    private ArrayList<Calls> getCalls(Calendar from, Calendar to) throws SQLException {
        ArrayList<Calls> calls = new ArrayList<>();

        String query = "SELECT calls.cid, calls.category, sum(calls.round_session_time) as time \n"
                + "FROM (SELECT s.id, s.cid, s.from_number_164, s.to_number_164, s.round_session_time, \n"
                + "IF (s.to_number_164 LIKE '8%', '1', \n"
                + "IF (SUBSTR(s.to_number_164, 2, 3) = SUBSTR(s.from_number_164, 2, 3) \n"
                + "or is_irk_mobile(s.to_number_164),'3','2') \n"
                + ") category \n"
                + "FROM log_session_18_201708_fraud s \n"
                + "WHERE s.session_start BETWEEN ? AND ? \n"
                + "LIMIT 10000) calls \n"
                + "GROUP BY calls.cid, calls.category";
        PreparedStatement ps = con.prepareStatement(query);
        //ps.setString(1, ServerUtils.getModuleMonthTableName(" log_session_", from.getTime(), 6));
        ps.setTimestamp(1, TimeUtils.convertCalendarToTimestamp(to));
        ps.setTimestamp(2, TimeUtils.convertCalendarToTimestamp(from));

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            calls.add(new Calls(rs.getInt("cid"), rs.getString("category"), rs.getInt("time")));
        }
        rs.close();
        return calls;
    }

    /**
     * Определение является абонент юридичиским или физическим лицом
     *
     * @param contract номер контракта
     * @return 0- физ. лицо, 1 - юр.лицо
     * @throws SQLException
     */
    private int WhatUser(int contract) throws SQLException {
        // определение пользователя (физическое или юридическое лицо)
        String query = "Select id, fc \n"
                + "FROM contract \n"
                + "Where id = " + contract;
        PreparedStatement ps = con.prepareStatement(query);
        ResultSet rs = ps.executeQuery();

        int typeUser = -1;
        while (rs.next()) {
            typeUser = rs.getInt("fc");
        }
        rs.close();
        return typeUser;
    }

    /**
     * Получение данных о пользователей, которых нельзя блокировать
     *
     * @param con
     * @return
     * @throws SQLException
     */
    private ArrayList<Users> getUsers() throws SQLException {
        ArrayList<Users> users = new ArrayList<>();
        String query = "SELECT id, cid \n"
                + "FROM exception";
        PreparedStatement ps = con.prepareStatement(query);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            users.add(new Users(rs.getInt("id"), rs.getInt("cid")));
        }
        rs.close();
        return users;
    }

    /**
     * Добавление информации о трафике абонента
     *
     * @param tr данные трафика
     * @param date дата добавления
     * @throws SQLException
     */
    private void AddDataInTraffic(Traffic tr, Calendar date) throws SQLException {
        String query = "INSERT INTO traffic (`id`, `contract_id`, `interzone`, `intercity`, `international`, `day`, `status`)\n"
                + " VALUES ('', ?, ?, ?, ?, ?, ?)";

        PreparedStatement ps = con.prepareStatement(query);
        ps.setInt(1, tr.getContract_id());
        ps.setInt(2, tr.getInterzone());
        ps.setInt(3, tr.getIntercity());
        ps.setInt(4, tr.getInternational());
        ps.setDate(5, TimeUtils.convertCalendarToSqlDate(date));
        ps.setInt(6, tr.getStatus());
        ps.executeUpdate();
    }

    /**
     * Блокировка абонента
     *
     * @param contract номер контракта
     * @throws Exception
     */
    private void LockContract(int contract) throws Exception {
        ContractDao cd = new ContractDao(conSet.getConnection(), 0);
        Contract c = cd.get(contract);
        c.setStatus((byte) 4);
        cd.update(c);
    }

    /**
     * Превышен ли лимит по внутризоновым телефонным соединениям для физических
     * лиц
     *
     * @param session количество выговоренных секунд
     * @return true - лимит превышен, false - лимит не превышен
     */
    private boolean InZoneNatural(int session) {
        return session > limitSecondsNaturalZone;
    }

    /**
     * Превышен ли лимит по внутризоновым телефонным соединениям для юридических
     * лиц
     *
     * @param session количество выговоренных секунд
     * @return true - лимит превышен, false - лимит не превышен
     */
    private boolean InZoneLegal(int session) {
        return session > limitSecondsLegalZone;
    }

    /**
     * Превышен ли лимит по межгородним телефонным соединениям для физических
     * лиц
     *
     * @param session количество выговоренных секунд
     * @return true - лимит превышен, false - лимит не превышен
     */
    private boolean IntercityNatural(int session) {
        return session > limitSecondsNaturalIntercity;
    }

    /**
     * Превышен ли лимит по межгородним телефонным соединениям для юридических
     * лиц
     *
     * @param session количество выговоренных секунд
     * @return true - лимит превышен, false - лимит не превышен
     */
    private boolean IntercityLegal(int session) {
        return session > limitSecondsLegalIntercity;
    }

    /**
     * Превышен ли лимит по международным телефонным соединениям
     *
     * @param session количество выговоренных секунд
     * @return true - лимит превышен, false - лимит не превышен
     */
    private boolean International(int session) {
        return session > limitSecondsInternational;
    }
}
