package antifroud;

import java.sql.Date;
import java.sql.Time;

/**
 * Информация о трафике абонента
 *
 * @author
 */
public class Traffic {

    /**
     * Данные трафика
     *
     * @param id идентификатор записи
     * @param contract_id номер контракта
     * @param interzone потраченные минуты по внутризоновым звонкам
     * @param intercity потраченные минуты по междугородним звонкам
     * @param international потраченные минуты по международным звонкам
     * @param day день звонка
     * @param status статус догвора
     * @param date1 дата начала предыдущей выборки
     * @param date2 дата конца предыдущей выборки
     */
    Traffic(int id, int contract_id, int interzone, int intercity, int international, Date day, int status, Time time1, Time time2) {
        this.id = id;
        this.cid = contract_id;
        this.interzone = interzone;
        this.intercity = intercity;
        this.international = international;
        this.day = day;
        this.status = status;
        this.from = time1;
        this.to = time2;
    }

    /**
     *
     * @param contract_id номер контракта
     */
    public Traffic(int contract_id) {
        this.cid = contract_id;
        this.intercity = 0;
        this.international = 0;
        this.interzone = 0;
    }

    /**
     * Идентификатор записи
     */
    private int id;

    /**
     * номер договора
     */
    private int cid;

    /**
     * Количество потраченных минут по внутризоновым звонкам
     */
    private int interzone;

    /**
     * Количество потраченных минут по городским звонкам
     */
    private int intercity;

    /**
     * Количество потраченных минут по международным звонкам
     */
    private int international;

    /**
     * День, за который происходят расчеты
     */
    private Date day;

    /**
     * Статус договора: 0 - подключен, 1 - на отключении, 2 - отключен, 3 -
     * закрыт, 4 - приостановлен, 5 - на подключении
     */
    private int status;

    /**
     * Дата начала прошлой выборки
     */
    private Time from;

    /**
     * Дата окончания прошлой выборки
     */
    private Time to;

    /**
     * Получение номера контракта
     *
     * @return номер контракта
     */
    public int getContract_id() {
        return cid;
    }

    /**
     * Получение количества минут по внутризоновым звонкам
     *
     * @return количество минут по внутризоновым звонкам
     */
    public int getInterzone() {
        return interzone;
    }

    /**
     * Получение количества минут по междугородним звонкам
     *
     * @return количество минут по междугородним звонкам
     */
    public int getIntercity() {
        return intercity;
    }

    /**
     * Получение количества минут по международным звонкам
     *
     * @return количество минут по международным звонкам
     */
    public int getInternational() {
        return international;
    }

    public int getStatus() {
        return status;
    }

    /**
     * Получение даты начала предыдущей выборки
     *
     * @return дата начала предыдущей выборки
     */
    public Time getDateFrom() {
        return from;
    }

    /**
     * Получение даты конца предыдущей выборки
     *
     * @return дата конца предыдущей выборки
     */
    public Time getDateTo() {
        return to;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public void setInterzone(int interzone) {
        this.interzone = interzone;
    }

    public void setIntercity(int intercity) {
        this.intercity = intercity;
    }

    public void setInternational(int international) {
        this.international = international;
    }
}
