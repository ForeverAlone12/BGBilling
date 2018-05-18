package ru.dsi.bgbilling.modules.phone.antifraud;

//package ru.dsi.fraud;
import java.util.HashMap;
import java.sql.ResultSet;
import java.util.Calendar;
import java.sql.Timestamp;
import java.sql.Connection;
import java.util.ArrayList;
import java.sql.SQLException;
import org.apache.log4j.Logger;
import java.sql.PreparedStatement;
import ru.bitel.common.ParameterMap;
import bitel.billing.common.TimeUtils;
import ru.bitel.common.sql.ConnectionSet;
import ru.bitel.bgbilling.server.util.Setup;
import ru.bitel.bgbilling.common.BGException;
import ru.bitel.bgbilling.kernel.contract.api.common.bean.Contract;
import ru.bitel.bgbilling.kernel.script.server.dev.GlobalScriptBase;
import ru.bitel.bgbilling.kernel.contract.api.server.bean.ContractDao;
import ru.bitel.bgbilling.server.util.ServerUtils;

public class Antifraud extends GlobalScriptBase {

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

    /**
     * Код модуля телефонии
     */
    private int codeTelephoneNumb;

    /**
     * Код объекта с настройками антифрода
     */
    private int codeObject;
    /**
     * Количество заблокированных абонентов
     */
    private int lock;

    /**
     * Название параметра объекта, определяющего трафик по внутризоновой связи
     */
    private String interzone;

    /**
     * Название параметра объекта, определяющего трафик по междугородней связи
     */
    private String intercity;
    /**
     * Название параметра объекта, определяющего трафик по международной связи
     */
    private String international;

    @Override
    public void execute(Setup setup, ConnectionSet connectionSet) throws Exception {

        // обработчик ошибок
        //Logger logger = Logger.getLogger(this.getClass());
        conSet = connectionSet;

        ParameterMap setting = setup.sub("ru.dsi.bgbilling.modules.phone.antifraud.");
        limitSecondsNaturalZone = setting.getInt("LIMIT_SECONDS_NATURAL_ZONE", 12000);
        limitSecondsLegalZone = setting.getInt("LIMIT_SECONDS_LEGAL_ZONE", 60000);
        limitSecondsNaturalIntercity = setting.getInt("LIMIT_SECONDS_NATURAL_INTERCITY", 12000);
        limitSecondsLegalIntercity = setting.getInt("LIMIT_SECONDS_LEGAL_INTERCITY", 60000);
        limitSecondsInternational = setting.getInt("LIMIT_SECONDS_INTERNATIONAL", 7200);
        codeTelephoneNumb = setting.getInt("codeTelephone", 6);
        codeObject = setting.getInt("codeObject", 1);
        interzone = setting.get("interzone", "Время внутризоновой связи");
        intercity = setting.get("intercity", "Время междугородней связи");
        international = setting.get("international", "Время международной связи");

        int recordate = setting.getInt("recordate", 0);

        Calendar to = Calendar.getInstance();
        Calendar from = (Calendar) to.clone();

        if (recordate == 0) {
            from.add(Calendar.HOUR_OF_DAY, -1);
        } else {
            from.setTime(Timestamp.valueOf(setting.get("startdate")));
            to.setTime(Timestamp.valueOf(setting.get("enddate")));
        }

        // проверка
        if (from.after(to)) {
            Calendar tmp = from;
            from = to;
            to = tmp;
        }

        HashMap<Integer, Traffic> traffic = new HashMap<>();
        ArrayList<Calls> calls = new ArrayList<>();
        Calls call; // данные звонка абонента
        Traffic tr; // текущий трафик абонента

        // информация о пользователях, которых нельзя блокировать
        HashMap<Integer, Users> users = new HashMap<>();

        Calendar start = (Calendar) from.clone();
        Calendar end = (Calendar) from.clone();
        end.add(Calendar.HOUR_OF_DAY, 1);

        int i;

        // Попытка подключения к БД
        try {
            con = connectionSet.getConnection();
        } catch (Exception ex) {
            //logger.error("Ошибка подключения к БД в скрипте Antifroud");
            //logger.error(ex.getMessage(), ex);
            throw new BGException("Ошибка подключения к БД в скрипте Antifroud\n" + ex.getMessage() + "\n" + ex);
        }
        try {
            users = getUsers();
            print("Количество пользователей с объектами: " + users.size());

        } catch (SQLException ex) {
            // logger.error("Не удалось извлечь данные  пользователях, которых нельзя блокировать\n");
            // logger.error(ex.getMessage(), ex);
            throw new BGException("Не удалось извлечь данные  пользователях, которых нельзя блокировать\n" + ex.getMessage() + "\n" + ex);
        }

        ContractDao cd = new ContractDao(con, 0);
        Contract contract;
        int def;
        while (!end.after(to)) {
            print("Начало выборки = " + start.getTime());
            print("Конец выборки = " + end.getTime());
            // считывание данных об обработанных звонках за день
            try {
                traffic = getTraffic(start, end);
            } catch (SQLException ex) {
                // logger.error("Не удалось извлечь данные об обработанных звонках за день. Время начала извлечения " + start.toString() + "\n");
                // logger.error(ex.getMessage(), ex);
                throw new BGException("Не удалось извлечь данные об обработанных звонках за день.\n"
                        + "Время начала извлечения " + start.toString() + "\n" + ex.getMessage() + "\n" + ex);
            }

            // Считывание необработанных звонков
            try {
                calls = getCalls(start, end);
            } catch (SQLException e) {
                //logger.error("Не удалось извлечь данные о звонках с " + start.toString() + " по " + end.toString() + "\n");
                //logger.error(e.getMessage(), e);
                throw new BGException("Не удалось извлечь данные о звонках с "
                        + start.toString() + " по " + end.toString() + "\n" + e.getMessage() + "\n" + e);
            }

            print("Количество необработанных звонков = " + calls.size());
            print("Количество данных трафика до обработки данных = " + traffic.size());

            lock = 0;

            // определение превышения трафика
            for (i = 0; i < calls.size(); i++) {
                print("№ необработанного звонка: " + (i + 1));

                // информация о необработанном звонке
                call = calls.get(i);

                try {
                    contract = cd.get(call.getContarct_id());
                    print("Номер договора = " + contract);

                } catch (NullPointerException e) {
                    // logger.error("Не удалось распознать договор. Номер контракта: " + call.getContarct_id());
                    // logger.error(e.getMessage(), e);
                    throw new BGException("Не удалось распознать договор. Номер контракта: " + call.getContarct_id() + "\n" + e.getMessage() + "\n" + e);
                }

                // получение данных о трафике абонента
                tr = new Traffic(call.getContarct_id(), ToTimestamp(start), ToTimestamp(end), call.getDate());
                print("Статус договора = " + tr.getStatus());

                try {     // вычисление длительности разговора
                    switch (call.getCategories()) {
                        case "1":
                            print("Международный звонок: " + call.getTime());
                            tr.setInternational(call.getTime());
                            break;
                        case "2":
                            print("Междугородний звонок: " + call.getTime());
                            tr.setIntercity(call.getTime());
                            break;
                        case "3":
                            print("Внутризоновый звонок: " + call.getTime());
                            tr.setInterzone(call.getTime());
                            break;
                        default:
                            throw new Exception("Неопознанный тип звонка");
                    }
                } catch (Exception ex) {
                    //  logger.error("Не удалось распознать тип звонка. Полученный тип : " + call.getCategories() + "\n");
                    //  logger.error(ex.getMessage(), ex);
                    throw new BGException("Не удалось распознать тип звонка. Полученный тип: " + call.getCategories() + "\n" + ex.getMessage() + "\n" + ex);
                }

                try {
                    UpdateDataInTraffic(tr, start, end);
                } catch (SQLException ex) {
                    throw new BGException("Ошибка вставки данных о трафике абонента: " + call.getContarct_id() + "\n" + ex.getMessage() + "\n" + ex);
                }

                def = 0;
                // проверка на превышение трафик производится только для абонентов,
                // не входящих в список исключений
                if (users.containsKey(call.getContarct_id())) {
                    def = 1;
                }

                if (tr.getStatus() != 4) { // если абонент заблокирован,то не надо блокировать его снова
                    print("Тип договора: " + contract.getPersonType());
                    try {
                        switch (contract.getPersonType()) {
                            case 0:
                                if (def == 0) {
                                    if (InZoneNatural(SumInterzone(tr)) || IntercityNatural(SumIntercity(tr)) || International(SumInternational(tr))) {
                                        LockContract(call.getContarct_id());
                                        tr.setStatus(4);
                                        print("У контракта " + contract + " сменился статус на " + tr.getStatus());
                                    }
                                } else {
                                    isExist(users, call.getContarct_id(), contract.getPersonType());
                                    if (InZoneNatural(SumInterzone(tr), users.get(call.getContarct_id()).getInterzone())
                                            || IntercityNatural(SumIntercity(tr), users.get(call.getContarct_id()).getIntercity())
                                            || International(SumInternational(tr), users.get(call.getContarct_id()).getInternational())) {
                                        LockContract(call.getContarct_id());
                                        tr.setStatus(4);
                                        print("У контракта " + contract + " сменился статус на " + tr.getStatus());
                                    }
                                }
                                break;
                            case 1:
                                if (def == 0) {
                                    if (InZoneLegal(SumInterzone(tr)) || IntercityLegal(SumIntercity(tr)) || International(SumInternational(tr))) {
                                        LockContract(call.getContarct_id());
                                        tr.setStatus(4);
                                        print("У контракта " + contract + " сменился статус на " + tr.getStatus());
                                    }
                                } else {
                                    isExist(users, call.getContarct_id(), contract.getPersonType());
                                    if (InZoneLegal(SumInterzone(tr), users.get(call.getContarct_id()).getInterzone())
                                            || IntercityLegal(SumIntercity(tr), users.get(call.getContarct_id()).getIntercity())
                                            || International(SumInternational(tr), users.get(call.getContarct_id()).getInternational())) {
                                        LockContract(call.getContarct_id());
                                        tr.setStatus(4);
                                        print("У контракта " + contract + " сменился статус на " + tr.getStatus());
                                    }
                                }
                                break;
                            default:
                                throw new Exception("Неопознанный тип договора");
                        }
                    } catch (SQLException e) {
                        // logger.error("Не удалось занести данные в БД. Номер контракта: " + call.getContarct_id());
                        // logger.error(ex.getMessage(), e);
                        throw new BGException("Не удалось добавить данные в БД. Номер контракта: " + call.getContarct_id() + "\n" + e.getMessage() + "\n" + e);
                    } catch (Exception ex) {
                        // logger.error("Не удалось распознать договор. Номер контракта: " + call.getContarct_id() + ". Полученный тип договра: " + contract.getPersonType());
                        // logger.error(ex.getMessage(), ex);
                        throw new BGException("Не удалось распознать договор. Номер контракта: " + call.getContarct_id() + ". Полученный тип договора: " + contract.getPersonType() + "\n" + ex.getMessage() + "\n" + ex);
                    }
                }
                try {
                    UpdateDataInTraffic(tr, start, end);
                    traffic.put(tr.getContract_id(), tr);

                } catch (SQLException ex) {
                    throw new BGException("Ошибка вставки данных о трафике абонента: " + call.getContarct_id() + "\n" + ex.getMessage() + "\n" + ex);
                }
                //}// if (users.contain...)

            }// for

            print("Количество данных трафика после обработки данных = " + traffic.size());
            print("Количество заблокированных абонентов " + lock);

            start = (Calendar) end.clone();
            end.add(Calendar.HOUR_OF_DAY, 1);
        }
    }

    private void isExist(HashMap<Integer, Users> user, int cid, int type) {
        if (user.get(cid).getInterzone() == 0) {
            if (type == 1) {
                user.get(cid).setInterzone(limitSecondsLegalZone);
            } else {
                user.get(cid).setInterzone(limitSecondsNaturalZone);
            }
        }

        if (user.get(cid).getIntercity() == 0) {
            if (type == 1) {
                user.get(cid).setInterzone(limitSecondsLegalIntercity);
            } else {
                user.get(cid).setInterzone(limitSecondsNaturalIntercity);
            }
        }

        if (user.get(cid).getInternational() == 0) {
            user.get(cid).setInterzone(limitSecondsInternational);
        }
    }

    /**
     * Преобразование Calendar в Timestamp
     *
     * @param calendar
     * @return
     */
    private Timestamp ToTimestamp(Calendar calendar) {
        return TimeUtils.convertCalendarToTimestamp(calendar);
    }

    /**
     * Получение данных о трафике абонентов
     *
     * @return
     * @throws SQLException
     */
    private HashMap<Integer, Traffic> getTraffic(Calendar from, Calendar to) throws SQLException {
        HashMap<Integer, Traffic> traffic = new HashMap<>();
        String query = "SELECT `id`, `cid`, `interzone`, `intercity`, `international`, `day`, `status`,`time1`, `time2` \n"
                + "FROM traffic \n"
                + "WHERE `day`=? \n";
        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setDate(1, TimeUtils.convertCalendarToSqlDate(from));
            //         ps.setTimestamp(2, TimeUtils.convertCalendarToTimestamp(from));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    traffic.put(rs.getInt("cid"), new Traffic(rs.getInt("id"),
                            rs.getInt("cid"),
                            rs.getInt("interzone"),
                            rs.getInt("intercity"),
                            rs.getInt("international"),
                            rs.getDate("day"),
                            rs.getByte("status"),
                            rs.getTimestamp("time1"),
                            rs.getTimestamp("time2")));
                }
            } catch (SQLException ex) {
                throw new SQLException();
            }
        }
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

        String query = "SELECT calls.`cid`, calls.`category`, sum(calls.`round_session_time`) as `time`, calls.`day` \n"
                + "FROM (SELECT s.`id`, s.`cid`, s.`from_number_164`, s.`to_number_164`, s.`round_session_time`, s.`session_start` as `day`, \n"
                + "IF (s.`to_number_164` LIKE '8%', '1', \n"
                + "IF (SUBSTR(s.`to_number_164`, 2, 3)=SUBSTR(s.`from_number_164`, 2, 3) \n"
                + "OR is_irk_mobile(s.`to_number_164`),'3','2') \n"
                + ") category \n"
                + "FROM " + ServerUtils.getModuleMonthTableName("log_session", from.getTime(), codeTelephoneNumb) + " s \n"
                + "WHERE s.`session_start` BETWEEN ? AND ? \n"
                + ") calls \n"
                + "GROUP BY calls.`cid`, calls.`category`";

        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setTimestamp(1, ToTimestamp(from));
            ps.setTimestamp(2, ToTimestamp(to));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    calls.add(new Calls(rs.getInt("cid"), rs.getString("category"), rs.getInt("time"), rs.getDate("day")));
                }
            } catch (SQLException ex) {
                throw new SQLException();
            }
        }
        return calls;

    }

    /**
     * Получение списка пользователей, которых нельзя блокировать
     *
     * @return список пользователей, которых нельзя блокировать
     * @throws SQLException
     */
    private HashMap<Integer, Users> getUsers() throws Exception {

        ArrayList<ParamValue> value = new ArrayList<>();
        HashMap<Integer, Users> us = new HashMap<>();

        String query = "SELECT o.`cid`, op.`title`, ot.`value`\n"
                + "FROM object o \n"
                + "LEFT JOIN object_param_value_text ot ON o.`id`=ot.`object_id` \n"
                + "LEFT JOIN object_param op ON ot.`param_id`=op.`id` \n"
                + "WHERE (o.date1 IS NULL OR o.`date1`<=curdate()) AND (o.`date2` IS NULL OR o.`date2`>=curdate()) \n"
                + "AND o.`type_id`=? \n"
                + "ORDER BY o.`cid`, ot.`param_id`";
        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, codeObject);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    value.add(new ParamValue(rs.getInt("cid"), rs.getString("title"), rs.getInt("value")));
                }
            } catch (SQLException ex) {
                throw new SQLException();
            }
        }

        Users u = new Users();
        for (ParamValue v : value) {
            if (v.getTitle().equals(interzone)) {
                u.setInterzone(v.getValue());
            }
            if (v.getTitle().equals(intercity)) {
                u.setIntercity(v.getValue());
            }
            if (v.getTitle().equals(international)) {
                u.setInternational(v.getValue());
            }
            us.putIfAbsent(v.getCid(), u);
        }

        return us;

    }

    /**
     * Обновление информации о данных трафика
     *
     * @param tr данные трафика
     * @param from время с которог производится выборка
     * @param to время по которое производится выборка
     * @throws SQLException
     */
    private void UpdateDataInTraffic(Traffic tr, Calendar from, Calendar to) throws SQLException {
        String query = "INSERT INTO traffic (`id`,`cid`, `interzone`, `intercity`, `international`, `day`, `status`,`time1`,`time2`)\n"
                + "VALUES ('',?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE \n"
                + "interzone=?, intercity=?, international=?, time1=?, time2=?";

        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, tr.getContract_id());
            ps.setInt(2, tr.getInterzone());
            ps.setInt(3, tr.getIntercity());
            ps.setInt(4, tr.getInternational());
            ps.setDate(5, TimeUtils.convertCalendarToSqlDate(from));
            ps.setInt(6, tr.getStatus());
            ps.setString(7, TimeUtils.convertCalendarToDateTimeString(from));
            ps.setString(8, TimeUtils.convertCalendarToDateTimeString(to));
            ps.setInt(9, tr.getInterzone());
            ps.setInt(10, tr.getIntercity());
            ps.setInt(11, tr.getInternational());
            ps.setString(12, TimeUtils.convertCalendarToDateTimeString(from));
            ps.setString(13, TimeUtils.convertCalendarToDateTimeString(to));
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new SQLException();
        }

    }

    /**
     * Блокировка абонента
     *
     * @param contract номер контракта
     * @throws Exception
     */
    private void LockContract(int contract) throws Exception {

        con.setAutoCommit(false);
        ContractDao cd = new ContractDao(con, 0);
        Contract c;
        try {
            // изменение статуса контракта в системе BGBilling

            c = cd.get(contract);

            c.setStatus((byte) 4);
            cd.update(c);
        } catch (Exception ex) {
            throw new Exception("Ошибка при попытки смены статуса договора абонента: " + contract);
        }

        String query;
        PreparedStatement ps;
        try {
            // занесение в таблицу lockabonent заблокированных абонентов
            query = "INSERT IGNORE INTO lockabonent (`id`, `fc`, `cid`) \n"
                    + " VALUES ('', ?, ?)";
            ps = con.prepareStatement(query);
            ps.setInt(1, c.getPersonType());
            ps.setInt(2, contract);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new SQLException("Ошибка при добавлении абонента " + contract + " в список заблокированных лиц");
        }

        try {
            // изменение данных в таблице traffic
            query = "UPDATE traffic SET `status`=4 WHERE `cid`=" + contract;
            ps = con.prepareStatement(query);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new SQLException("Ошибка при смене абонента " + contract + " в таблице traffic");
        }
        ps.close();

        con.commit();

        print("Контракт " + contract + " заблокирован!!!");
        lock++;

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

    /**
     * Превышен ли лимит по внутризоновым телефонным соединениям для физических
     * лиц
     *
     * @param session количество выговоренных секунд
     * @return true - лимит превышен, false - лимит не превышен
     */
    private boolean InZoneNatural(int session, int limit) {
        return session > limit;
    }

    /**
     * Превышен ли лимит по внутризоновым телефонным соединениям для юридических
     * лиц
     *
     * @param session количество выговоренных секунд
     * @return true - лимит превышен, false - лимит не превышен
     */
    private boolean InZoneLegal(int session, int limit) {
        return session > limit;
    }

    /**
     * Превышен ли лимит по межгородним телефонным соединениям для физических
     * лиц
     *
     * @param session количество выговоренных секунд
     * @return true - лимит превышен, false - лимит не превышен
     */
    private boolean IntercityNatural(int session, int limit) {
        return session > limit;
    }

    /**
     * Превышен ли лимит по межгородним телефонным соединениям для юридических
     * лиц
     *
     * @param session количество выговоренных секунд
     * @return true - лимит превышен, false - лимит не превышен
     */
    private boolean IntercityLegal(int session, int limit) {
        return session > limit;
    }

    /**
     * Превышен ли лимит по международным телефонным соединениям
     *
     * @param session количество выговоренных секунд
     * @return true - лимит превышен, false - лимит не превышен
     */
    private boolean International(int session, int limit) {
        return session > limit;
    }

    /**
     * Получение длительности разговора за день по внутризоновому соединению
     *
     * @param traffic данные трафика
     * @return длительность разговора за день по внутризоновому соединению
     * @throws SQLException
     */
    private int SumInterzone(Traffic traffic) throws Exception {
        String query = "SELECT sum(`interzone`) as `sum_interzone` \n"
                + "FROM traffic \n"
                + "WHERE `cid`=? AND `day`=?";
        int rezult = -1;
        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, traffic.getContract_id());
            ps.setDate(2, traffic.getDate());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rezult = rs.getInt("sum_interzone");
                }
            } catch (SQLException ex) {
                throw new SQLException("Ошибка в запросе вычисления общего количества времени разговора по внутризоновым соединениям");
            }
        }
        if (rezult == -1) {
            throw new Exception("Не удалось вычислить длительность разговора по внутризоновому соединению");
        }
        return rezult;
    }

    /**
     * Получение длительности разговора за день по междугороднему соединению
     *
     * @param traffic данные трафика
     * @return длительность разговора за день по междугороднему соединению
     * @throws Exception
     */
    private int SumIntercity(Traffic traffic) throws Exception {
        String query = "SELECT sum(`intercity`) as `sum_intercity` \n"
                + "FROM traffic \n"
                + "WHERE `cid`=? AND `day`=?";
        int rezult = -1;
        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, traffic.getContract_id());
            ps.setDate(2, traffic.getDate());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rezult = rs.getInt("sum_intercity");
                }
            } catch (SQLException ex) {
                throw new SQLException("Ошибка в запросе вычисления общего количества времени разговора по междугородним соединениям");
            }
        }
        if (rezult == -1) {
            throw new Exception("Не удалось вычислить длительность разговора по междугороднему соединению");
        }
        return rezult;
    }

    /**
     * Получение длительности разговора за день по международному соединению
     *
     * @param traffic данные трафика
     * @return длительность разговора за день по международному соединению
     * @throws Exception
     */
    private int SumInternational(Traffic traffic) throws Exception {
        String query = "SELECT sum(`international`) as `sum_international` \n"
                + "FROM traffic \n"
                + "WHERE `cid`=? AND `day`=?";
        int rezult = -1;
        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, traffic.getContract_id());
            ps.setDate(2, traffic.getDate());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rezult = rs.getInt("sum_international");
                }
            } catch (SQLException ex) {
                throw new SQLException("Ошибка в запросе вычисления общего количества времени разговора по международным соединениям");
            }
        }
        if (rezult == -1) {
            throw new Exception("Не удалось вычислить длительность разговора по международному соединению");
        }
        return rezult;
    }
}
