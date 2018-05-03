package antifroud;

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

    private int lok = 0;

    @Override
    public void execute(Setup setup, ConnectionSet connectionSet) throws Exception {

        // обработчик ошибок
        Logger logger = Logger.getLogger(this.getClass());

        conSet = connectionSet;

        ParameterMap setting = setup.sub("ru.dsi.fraud");
        limitSecondsNaturalZone = setting.getInt("LIMIT_SECONDS_NATURAL_ZONE", 12000);
        limitSecondsLegalZone = setting.getInt("LIMIT_SECONDS_LEGAL_ZONE", 60000);
        limitSecondsNaturalIntercity = setting.getInt("LIMIT_SECONDS_NATURAL_INTERCITY", 12000);
        limitSecondsLegalIntercity = setting.getInt("LIMIT_SECONDS_LEGAL_INTERCITY", 60000);
        limitSecondsInternational = setting.getInt("LIMIT_SECONDS_INTERNATIONAL", 7200);
        int recordate = setting.getInt("recordate", 0);

        Calendar to = Calendar.getInstance();
        Calendar from = Calendar.getInstance();
        if (recordate == 0) {
            // --- удалить на боевой версии
            to.set(Calendar.YEAR, 2017);
            //месяцы в Calendar нумеруются с 0. Так исторически сложилось :)
            to.set(Calendar.MONTH, 7);
            to.set(Calendar.DAY_OF_MONTH, 1);
            to.set(Calendar.HOUR_OF_DAY, 0);
            to.set(Calendar.MINUTE, 0);
            to.set(Calendar.SECOND, 0);
            // --- конец удаления    

            from = (Calendar) to.clone();
            from.add(Calendar.HOUR_OF_DAY, -1);
        } else {        
            from.setTime(Timestamp.valueOf(setting.get("startdate")));
            to.setTime(Timestamp.valueOf(setting.get("enddate")));
        }

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
            traffic = getTraffic(from, to);
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

        print("Количество необработанных звонков = " + calls.size());
        print("Количество данных трафика до обработки данных = " + traffic.size());

        Calls call; // данные звонка абонента
        Traffic tr; // текущий трафик абонента
        ContractDao cd = new ContractDao(connectionSet.getConnection(), 0);
        Contract contract;
        // информация о пользователях, которых нельзя блокировать
        ArrayList<Users> users = new ArrayList<>();
        try {
            users = getUsers();
        } catch (SQLException ex) {
            logger.error("Не удалось извлечь данные  пользователях, которых нельзя блокировать\n");
            logger.error(ex.getMessage(), ex);
            throw new BGException("Не удалось извлечь данные  пользователях, которых нельзя блокировать\n");
        }

        // определение превышения трафика
        for (int i = 0; i < calls.size(); i++) {
            print("№ необработанного звонка: " + (i + 1));

            // информация о необработанном звонке
            call = calls.get(i);

            contract = cd.get(call.getContarct_id());
            if (contract == null) {
                print("NULL CONTRACT " + call.getContarct_id());
            }
            print(contract.getId());

            // получение данных о трафике абонента
            tr = traffic.getOrDefault(call.getContarct_id(), new Traffic(call.getContarct_id()));

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
                logger.error("Не удалось распознать тип звонка. Полученный тип : " + call.getCategories() + "\n");
                logger.error(ex.getMessage(), ex);
                throw new BGException("Не удалось распознать тип звонка. Полученный тип: " + call.getCategories() + "\n");
            }

            // проверка на превышение трафик производится только для абонентов,
            // не входящих в список исключений
            if (!users.contains(call.getContarct_id())) {

                print("Тип договора: " + contract.getPersonType());
                try {
                    switch (contract.getPersonType()) {
                        case 0:
                            if (InZoneNatural(tr.getInterzone()) || IntercityNatural(tr.getIntercity()) || International(tr.getInternational())) {
                                LockContract(call.getContarct_id());
                                tr.setStatus(0);
                            }
                            break;
                        case 1:
                            if (InZoneLegal(tr.getInterzone()) || IntercityLegal(tr.getIntercity()) || International(tr.getInternational())) {
                                LockContract(call.getContarct_id());
                                tr.setStatus(0);
                            }
                            break;
                        default:
                            throw new Exception("Неопознанный тип договора");
                    }
                } catch (NullPointerException e) {
                    logger.error("Не удалось распознать договор. Номер контракта: " + call.getContarct_id());
                    logger.error(e.getMessage(), e);
                    throw new BGException("Не удалось распознать договор. Номер контракта: " + call.getContarct_id());
                } catch (Exception ex) {
                    logger.error("Не удалось распознать договор. Номер контракта: " + call.getContarct_id() + ". Полученный тип договра: " + contract.getPersonType());
                    logger.error(ex.getMessage(), ex);
                    throw new BGException("Не удалось распознать договор. Номер контракта: " + call.getContarct_id() + ". Полученный тип договора: " + contract.getPersonType());
                }
                try {
                    AddDataInTraffic(tr, from, to);
                    // если нет данных о звонках
                    if (!traffic.containsKey(call.getContarct_id())) {
                        traffic.put(tr.getContract_id(), tr);
                    }

                } catch (SQLException ex) {
                    logger.error("Ошибка вставки данных о звонках абонента: " + call.getContarct_id());
                    logger.error(ex.getMessage(), ex);
                    throw new BGException("Ошибка вставки данных о звонках абонента: " + call.getContarct_id());
                }
            }// if (users.contain...)
        }// for

        print("Количество данных трафика после обработки данных = " + traffic.size());
        print("Количество заблокированных абонентов " + lok);
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
                + "FROM traffic  \n"
                + "WHERE (`status`=0) AND (`day`=?) AND (?>`time2`)"
                + "GROUP BY `cid`";
        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setDate(1, TimeUtils.convertCalendarToSqlDate(from));
            ps.setTimestamp(2, TimeUtils.convertCalendarToTimestamp(from));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    traffic.put(rs.getInt("cid"), new Traffic(rs.getInt("id"),
                            rs.getInt("cid"),
                            rs.getInt("interzone"),
                            rs.getInt("intercity"),
                            rs.getInt("international"),
                            rs.getDate("day"),
                            rs.getByte("status"),
                            rs.getTime("date1"),
                            rs.getTime("date2")));
                }
            }
        } finally {
            return traffic;
        }
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

        String query = "SELECT calls.`cid`, calls.`category`, sum(calls.`round_session_time`) as `time` \n"
                + "FROM (SELECT s.`id`, s.`cid`, s.`from_number_164`, s.`to_number_164`, s.`round_session_time`, \n"
                + "IF (s.`to_number_164` LIKE '8%', '1', \n"
                + "IF (SUBSTR(s.`to_number_164`, 2, 3)=SUBSTR(s.`from_number_164`, 2, 3) \n"
                + "OR is_irk_mobile(s.`to_number_164`),'3','2') \n"
                + ") category \n"
                + "FROM log_session_18_201708_fraud s \n"
                + "WHERE s.`session_start` BETWEEN ? AND ? \n"
                + "AND (s.`cid` NOT IN (SELECT t.`cid` FROM traffic t WHERE t.`status`=4)) \n" // убрать на рабочей машине
                + "AND  \n"
                + "LIMIT 10000) calls \n"
                + "GROUP BY calls.`cid`, calls.`category`";

        try (PreparedStatement ps = con.prepareStatement(query)) {
            //ps.setString(1, ServerUtils.getModuleMonthTableName(" log_session_", from.getTime(), 6));
            ps.setTimestamp(1, TimeUtils.convertCalendarToTimestamp(from));
            ps.setTimestamp(2, TimeUtils.convertCalendarToTimestamp(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    calls.add(new Calls(rs.getInt("cid"), rs.getString("category"), rs.getInt("time")));
                }
            }
        } finally {
            return calls;
        }

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
     * @param date дата добавления
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
            ps.setTimestamp(7, TimeUtils.convertCalendarToTimestamp(from));
            ps.setTimestamp(8, TimeUtils.convertCalendarToTimestamp(to));
            ps.setInt(9, tr.getInterzone());
            ps.setInt(10, tr.getIntercity());
            ps.setInt(11, tr.getInternational());
            ps.setTimestamp(12, TimeUtils.convertCalendarToTimestamp(from));
            ps.setTimestamp(13, TimeUtils.convertCalendarToTimestamp(to));
            ps.executeUpdate();
        }
    }

    /**
     * Блокировка абонента
     *
     * @param contract номер контракта
     * @throws Exception
     */
    private void LockContract(int contract) throws Exception {

        con = conSet.getConnection();
        boolean autocommit = con.getAutoCommit();
        con.setAutoCommit(false);

        // изменение статуса контракта в системе BGBilling
        ContractDao cd = new ContractDao(conSet.getConnection(), 0);
        Contract c = cd.get(contract);

        c.setStatus((byte) 4);
        cd.update(c);

        // занесение в таблицу lockabonent заблокированных абонентов
        String query = "INSERT INTO lockabonent (`id`, `fc`, `cid`) \n"
                + " VALUES ('', ?, ?)";
        PreparedStatement ps = con.prepareStatement(query);
        ps.setInt(1, c.getPersonType());
        ps.setInt(2, contract);
        ps.executeUpdate();

        // изменение данных в таблице traffic
        query = "UPDATE traffic SET `status`=4 WHERE `cid`=" + contract;
        ps = con.prepareStatement(query);
        ps.executeUpdate();

        ps.close();

        con.setAutoCommit(autocommit);

        print("!!!!!!!!!! контракт " + contract + " заблокирован!!!");
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
}
