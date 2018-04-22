package antifroud;

import bitel.billing.common.TimeUtils;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;

import java.util.Date;
import java.util.HashMap;
import ru.bitel.bgbilling.kernel.script.server.dev.GlobalScriptBase;
import ru.bitel.bgbilling.server.util.Setup;
import ru.bitel.common.sql.ConnectionSet;
import org.apache.log4j.Logger;
import ru.bitel.bgbilling.kernel.contract.api.common.bean.Contract;
import ru.bitel.bgbilling.kernel.contract.api.server.bean.ContractDao;
import ru.bitel.bgbilling.server.util.ServerUtils;
import ru.bitel.common.ParameterMap;

public class Antifroud extends GlobalScriptBase {

    private Connection con;
    private ConnectionSet conSet;
    private ParameterMap setting;

    @Override
    public void execute(Setup setup, ConnectionSet connectionSet) throws Exception {

        // обработчик ошибок
        Logger logger = Logger.getLogger(this.getClass());

        conSet = connectionSet;

        setting = setup.sub("fraud");

        boolean isFail = false;

        // определение текущего времени
        Calendar from = Calendar.getInstance();

        // --- удалить на боевой версии
        from.set(Calendar.YEAR, 2018);
        //месяцы в Calendar нумеруются с 0. Так исторически сложилось :)
        from.set(Calendar.MONTH, 7);
        from.set(Calendar.DAY_OF_MONTH, 1);
        from.set(Calendar.HOUR_OF_DAY, 0);
        from.set(Calendar.MINUTE, 0);
        from.set(Calendar.SECOND, 0);
        // --- конец удаления       

        Calendar to = (Calendar) from.clone();
        to.add(Calendar.HOUR_OF_DAY, 1);

        // Попытка подключения к БД
        try {
            con = connectionSet.getConnection();
        } catch (Exception ex) {
            logger.error("Не удалось подключиться к БД\n");
            logger.error(ex.getMessage(), ex);
            isFail = true;
        }

        if (isFail) {
            // считывание данных об обработанных звонках за день
            HashMap<Integer, Traffic> traffic = new HashMap<>();
            try {
                traffic = getTraffic();
            } catch (SQLException ex) {
                logger.error("Не удалось извлечь данные об обработанных звонках за день.\n"
                        + "Время начала извлечения " + from.toString());
                logger.error(ex.getMessage(), ex);
                isFail = true;
            }

            if (isFail) {
                // Считывание необработанных звонков
                ArrayList<Calls> calls = new ArrayList<>();
                try {
                    calls = getCalls(from, to);
                } catch (SQLException e) {
                    logger.error("Не удалось извлечь данные о звонках с "
                            + from.toString() + " по " + to.toString() + "\n");
                    logger.error(e.getMessage(), e);
                    isFail = true;
                }

                if (isFail) {
                    Calls call; // данные звонка абонента
                    Traffic tr; // текущий трафик абонента

                    // определение превышения трафика
                    for (int i = 0; i < calls.size(); i++) {
                        call = calls.get(i);
                        int contract = call.getContarct_id();
                        int typeUser = 0; // тип пользователя: 0 - физ. лицо, 1 - юр.лицо

                        try {
                            typeUser = WhatUser(contract);
                        } catch (SQLException e) {
                            logger.error("Не удалось извлечь данные о пользователе c id = " + contract + "\n");
                            logger.error(e.getMessage(), e);
                            isFail = true;
                        }
                        if (isFail) {
                            String typeCall = call.getCategories();
                            tr = traffic.getOrDefault(call.getContarct_id(), new Traffic(call.getContarct_id()));
                            // tr = traffic.get(call.getContarct_id());

                            // если есть данные о звонках
                            if (traffic.containsKey(call.getContarct_id())) {

                                try {
                                    switch (typeCall) {
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
                                    logger.error("Не удалось распознать тип звонка. Полученный тип: " + typeCall + "\n");
                                    logger.error(ex.getMessage(), ex);
                                    isFail = true;
                                }

                                if (isFail) {
                                    // получение информации о пользователях, которых нельзя блокировать
                                    ArrayList<Users> users = new ArrayList<>();
                                    try {
                                        users = getUsers();
                                    } catch (SQLException ex) {
                                        logger.error("Не удалось извлечь данные  пользователях, которых нельзя блокировать\n");
                                        logger.error(ex.getMessage(), ex);
                                        isFail = true;
                                    }

                                    if (isFail) {

                                        if (!users.contains(call.getContarct_id())) {
                                            try {
                                                switch (typeUser) {
                                                    case 0:
                                                        if (InZoneNatural(tr.getInterzone()) || IntercityNatural(tr.getIntercity()) || International(tr.getInternational())) {
                                                            LockContract(connectionSet, contract);
                                                            tr.setStatus((byte) 0);
                                                        }
                                                        break;
                                                    case 1:
                                                        if (InZoneLegal(tr.getInterzone()) || IntercityLegal(tr.getIntercity()) || International(tr.getInternational())) {
                                                            LockContract(connectionSet, contract);
                                                            tr.setStatus((byte) 0);
                                                        }
                                                        break;
                                                    default:
                                                        throw new Exception("Неопознанный тип абонента");
                                                }
                                            } catch (Exception ex) {
                                                logger.error("Не удалось распознать абонента. Номер контракта: " + contract + ". Полученный тип абонента: " + typeUser);
                                                logger.error(ex.getMessage(), ex);
                                                isFail = true;
                                            }
                                           
                                            try {
                                                AddDataInTraffic(tr, from);
                                            } catch (SQLException ex) {
                                                logger.error("Ошибка вставки данных о звонках абонента: " + contract);
                                                logger.error(ex.getMessage(), ex);
                                                isFail = true;
                                            }

                                        } else { // информации о звонках пользователя нет
                                            try {
                                                AddDataInTraffic(tr, from);
                                            } catch (SQLException ex) {
                                                logger.error("Ошибка вставки данных о звонках абонента: " + contract);
                                                logger.error(ex.getMessage(), ex);
                                                isFail = true;
                                            }
                                        }
                                    }

                                }
                            }
                        }
                    } // for
                }
            }

        }// if

    }

    /**
     * Получение данных о трафике абонента
     *
     * @param con
     * @return
     * @throws SQLException
     */
    private HashMap<Integer, Traffic> getTraffic() throws SQLException {
        HashMap<Integer, Traffic> traffic = new HashMap<>();
        String query = "SELECT id, contract_id, interzone, international, day, status \n"
                + "FROM traffic \n"
                + "WHERE status = false \n"
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
            byte status = rs.getByte("status");
            traffic.put(contract_id, new Traffic(id, contract_id, interzone, intercity, international, day, status));
        }
        return traffic;
    }

    /**
     * выбор данных о звонках в виде "номер_договора, категория звонка,
     * длительность звонка"
     *
     * @param con
     * @param from
     * @param to
     * @return
     * @throws SQLException
     */
    private ArrayList<Calls> getCalls(Calendar from, Calendar to) throws SQLException {
        ArrayList<Calls> calls = new ArrayList<>();
        // 
        // входные параметры:
        // 1) с какого  периода произодится выборка
        // 2) по каккой период производится выборка
        // 3) имя таблицы, из которой производится выборка
        String query = "SELECT calls.cid, calls.category, sum(calls.round_session_time) as time \n"
                + "FROM (SELECT s.id, s.cid, s.from_number_164, s.to_number_164, s.round_session_time, \n"
                + "IF (s.to_number_164 LIKE '8%', '1', \n"
                + "(SUBSTR(s.to_number_164, 2, 3) = SUBSTR(s.from_number_164, 2, 3) \n"
                + "or isIrkMobile(s.to_number_164),'3','2') \n"
                + ") category \n"
                + "FROM ? s \n"
                + "WHERE s.session_start BETWEEN ? AND ? \n"
                + "LIMIT 10000) calls \n"
                + "GROUP BY calls.cid, calls.category";
        PreparedStatement ps = con.prepareStatement(query);
        ps.setString(1, ServerUtils.getModuleMonthTableName(" log_session_", from.getTime(), 18));
        ps.setTimestamp(2, TimeUtils.convertCalendarToTimestamp(to));
        ps.setTimestamp(3, TimeUtils.convertCalendarToTimestamp(from));

        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            int contract_id = rs.getInt("contract_id");
            String categories = rs.getString("category");
            int session_time = rs.getInt("time");
            calls.add(new Calls(contract_id, categories, session_time));
        }
        return calls;
    }

    /**
     * Определение является абонент юридичиским или физическим лицом
     *
     * @param con
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
            int id = rs.getInt("id");
            int cid = rs.getInt("cid");
            users.add(new Users(id, cid));
        }
        return users;
    }

    /**
     * Добавление информации о трафике абонента
     *
     * @param con
     * @param tr данные трафика
     * @param date
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
     * @param connectionSet
     * @param contract номер контракта
     * @throws Exception
     */
    private void LockContract(ConnectionSet connectionSet, int contract) throws Exception {
        ContractDao cd = new ContractDao(connectionSet.getConnection(), 0);
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
        return session > setting.getInt("LIMIT_SECONDS_NATURAL_ZONE", 1200);
    }

    /**
     * Превышен ли лимит по внутризоновым телефонным соединениям для юридических
     * лиц
     *
     * @param session количество выговоренных секунд
     * @return true - лимит превышен, false - лимит не превышен
     */
    private boolean InZoneLegal(int session) {
        return session > setting.getInt("LIMIT_SECONDS_LEGAL_ZONE", 60000);
    }

    /**
     * Превышен ли лимит по межгородним телефонным соединениям для физических
     * лиц
     *
     * @param session количество выговоренных секунд
     * @return true - лимит превышен, false - лимит не превышен
     */
    private boolean IntercityNatural(int session) {
        return session > setting.getInt("LIMIT_SECONDS_NATURAL_INTERCITY", 1200);
    }

    /**
     * Превышен ли лимит по межгородним телефонным соединениям для юридических
     * лиц
     *
     * @param session количество выговоренных секунд
     * @return true - лимит превышен, false - лимит не превышен
     */
    private boolean IntercityLegal(int session) {
        return session > setting.getInt("LIMIT_SECONDS_LEGAL_INTERCITY", 60000);
    }

    /**
     * Превышен ли лимит по международным телефонным соединениям
     *
     * @param session количество выговоренных секунд
     * @return true - лимит превышен, false - лимит не превышен
     */
    private boolean International(int session) {
        return session > setting.getInt("LIMIT_SECONDS_INTERNATIONAL", 1200);
    }
}
