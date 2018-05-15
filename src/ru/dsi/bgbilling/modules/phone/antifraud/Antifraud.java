package ru.dsi.bgbilling.modules.phone.antifraud;

//package ru.dsi.fraud;
import java.util.HashMap;
import java.sql.ResultSet;
import java.util.Calendar;
import java.sql.Connection;
import java.util.ArrayList;
import java.sql.SQLException;
import org.apache.log4j.Logger;
import java.sql.PreparedStatement;
import ru.bitel.common.ParameterMap;
import bitel.billing.common.TimeUtils;
import java.sql.Timestamp;
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
     * количество заблокированных абонентов
     */
    private int lok;

    @Override
    public void execute(Setup setup, ConnectionSet connectionSet) throws Exception {

        // обработчик ошибок
        //Logger logger = Logger.getLogger(this.getClass());
        conSet = connectionSet;

        ParameterMap setting = setup.sub("ru.dsi.fraud.");
        limitSecondsNaturalZone = setting.getInt("LIMIT_SECONDS_NATURAL_ZONE", 12000);
        limitSecondsLegalZone = setting.getInt("LIMIT_SECONDS_LEGAL_ZONE", 60000);
        limitSecondsNaturalIntercity = setting.getInt("LIMIT_SECONDS_NATURAL_INTERCITY", 12000);
        limitSecondsLegalIntercity = setting.getInt("LIMIT_SECONDS_LEGAL_INTERCITY", 60000);
        limitSecondsInternational = setting.getInt("LIMIT_SECONDS_INTERNATIONAL", 7200);
        codeTelephoneNumb = setting.getInt("codeTelephone", 6);

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

        print("Начало выборки = " + from.getTime());
        print("Конец выборки = " + to.getTime());
        // Попытка подключения к БД
        try {
            con = connectionSet.getConnection();
        } catch (Exception ex) {
            //logger.error("Ошибка подключения к БД в скрипте Antifroud");
            //logger.error(ex.getMessage(), ex);
            throw new BGException("Ошибка подключения к БД в скрипте Antifroud\n" + ex.getMessage() + "\n" + ex);
        }

        // считывание данных об обработанных звонках за день
        HashMap<Integer, Traffic> traffic = new HashMap<>();
        try {
            traffic = getTraffic(from, to);
        } catch (SQLException ex) {
            // logger.error("Не удалось извлечь данные об обработанных звонках за день. Время начала извлечения " + from.toString() + "\n");
            // logger.error(ex.getMessage(), ex);
            throw new BGException("Не удалось извлечь данные об обработанных звонках за день.\n"
                    + "Время начала извлечения " + from.toString() + "\n" + ex.getMessage() + "\n" + ex);
        }

        // Считывание необработанных звонков
        ArrayList<Calls> calls = new ArrayList<>();
        try {
            calls = getCalls(from, to);
        } catch (SQLException e) {
            //logger.error("Не удалось извлечь данные о звонках с " + from.toString() + " по " + to.toString() + "\n");
            //logger.error(e.getMessage(), e);
            throw new BGException("Не удалось извлечь данные о звонках с "
                    + from.toString() + " по " + to.toString() + "\n" + e.getMessage() + "\n" + e);
        }

        print("Количество необработанных звонков = " + calls.size());
        print("Количество данных трафика до обработки данных = " + traffic.size());

        Calls call; // данные звонка абонента
        Traffic tr; // текущий трафик абонента
        ContractDao cd = new ContractDao(con, 0);
        Contract contract = null;
        // информация о пользователях, которых нельзя блокировать
        ArrayList<Users> users = new ArrayList<>();
        try {
            users = getUsers();
        } catch (SQLException ex) {
            // logger.error("Не удалось извлечь данные  пользователях, которых нельзя блокировать\n");
            // logger.error(ex.getMessage(), ex);
            throw new BGException("Не удалось извлечь данные  пользователях, которых нельзя блокировать\n" + ex.getMessage() + "\n" + ex);
        }

        lok = 0;

        // определение превышения трафика
        for (int i = 0; i < calls.size(); i++) {
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
            tr = new Traffic(call.getContarct_id(), ToTimestamp(from), ToTimestamp(to), call.getDate());
            print("Статус договора = " + tr.getStatus());

            try {     // вычисление длительности разговора

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
                //  logger.error("Не удалось распознать тип звонка. Полученный тип : " + call.getCategories() + "\n");
                //  logger.error(ex.getMessage(), ex);
                throw new BGException("Не удалось распознать тип звонка. Полученный тип: " + call.getCategories() + "\n" + ex.getMessage() + "\n" + ex);
            }

            // проверка на превышение трафик производится только для абонентов,
            // не входящих в список исключений
            if (!users.contains(call.getContarct_id())) {

                if (tr.getStatus() != 4) { // если абонент заблокирован,то не надо блокировать его снова
                    print("Тип договора: " + contract.getPersonType());
                    try {
                        switch (contract.getPersonType()) {
                            case 0:
                                if (InZoneNatural(SumInterzone(tr)) || IntercityNatural(SumIntercity(tr)) || International(SumInternational(tr))) {
                                    LockContract(call.getContarct_id());
                                    tr.setStatus(4);
                                    print("У контракта " + contract + " сменился статус на " + tr.getStatus());
                                }
                                break;
                            case 1:
                                if (InZoneLegal(SumInterzone(tr)) || IntercityLegal(SumIntercity(tr)) || International(SumInternational(tr))) {
                                    LockContract(call.getContarct_id());
                                    tr.setStatus(4);
                                    print("У контракта " + contract + " сменился статус на " + tr.getStatus());
                                }
                                break;
                            default:
                                throw new Exception("Неопознанный тип договора");
                        }
                    } catch (SQLException e) {
                        // logger.error("Не удалось занести данные в БД. Номер контракта: " + call.getContarct_id());
                        // logger.error(ex.getMessage(), e);
                        throw new BGException("Не удалось данные в БД. Номер контракта: " + call.getContarct_id() + "\n" + e.getMessage() + "\n" + e);
                    } catch (Exception ex) {
                        // logger.error("Не удалось распознать договор. Номер контракта: " + call.getContarct_id() + ". Полученный тип договра: " + contract.getPersonType());
                        // logger.error(ex.getMessage(), ex);
                        throw new BGException("Не удалось распознать договор. Номер контракта: " + call.getContarct_id() + ". Полученный тип договора: " + contract.getPersonType() + "\n" + ex.getMessage() + "\n" + ex);
                    }
                }
                try {
                    UpdateDataInTraffic(tr, from, to);

                    // если нет данных о звонках
                    if (!traffic.containsKey(call.getContarct_id())) {
                        traffic.put(tr.getContract_id(), tr);
                    }
                } catch (SQLException ex) {
                    throw new BGException("Ошибка вставки данных о трафике абонента: " + call.getContarct_id() + "\n" + ex.getMessage() + "\n" + ex);
                }
            }// if (users.contain...)

        }// for

        print("Количество данных трафика после обработки данных = " + traffic.size());
        print("Количество заблокированных абонентов " + lok);
    }

    /**
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
     * Осуществлялась выборка данных с from по to
     *
     * @param tr данные трафика
     * @param from начало выборки
     * @param to конец выборки
     * @return trrue - диапозон выборки принадлежит диапозону прошлой выборки,
     * иначе - false
     */
    private boolean isExists(Traffic tr, Calendar from, Calendar to) {
        return (ToTimestamp(from).after(tr.getDateFrom()) && ToTimestamp(from).before(tr.getDateTo()) || ToTimestamp(from).equals(tr.getDateFrom())) // левая граница входит в промежуток
                || (ToTimestamp(to).before(tr.getDateTo()) && ToTimestamp(to).after(tr.getDateFrom()) || ToTimestamp(to).equals(tr.getDateTo())); // правая граница входит в промежуток
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
     * Получение данных о пользователей, которых нельзя блокировать
     *
     * @param con
     * @return
     * @throws SQLException
     */
    private ArrayList<Users> getUsers() throws SQLException {
        ArrayList<Users> users = new ArrayList<>();
        String query = "SELECT `id`, `cid` \n"
                + "FROM exception";
        try (PreparedStatement ps = con.prepareStatement(query);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(new Users(rs.getInt("id"), rs.getInt("cid")));
            }
        } finally {
            return users;
        }
    }

    /**
     * Добавление информации о трафике абонента
     *
     * @param tr данные трафика
     * @param from начало выборки
     * @param toконец выборки
     * @throws SQLException
     */
    private void AddDataInTraffic(Traffic tr, Calendar from, Calendar to) throws SQLException {
        String query = "INSERT INTO traffic (`id`,`cid`, `interzone`, `intercity`, `international`, `day`, `status`,`time1`,`time2`)\n"
                + "VALUES ('',?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE \n"
                + "interzone=interzone+?, intercity=intercity+?, international=international+?, time1=?, time2=?";

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
     * Обновление информации о данных трафика
     *
     * @param tr
     * @param from
     * @param to
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
        lok++;

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

    private int SumInterzone(Traffic traffic) throws SQLException {
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
        return rezult;
    }

    private int SumIntercity(Traffic traffic) throws SQLException {
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
        return rezult;
    }

    private int SumInternational(Traffic traffic) throws SQLException {
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
        return rezult;
    }
}
