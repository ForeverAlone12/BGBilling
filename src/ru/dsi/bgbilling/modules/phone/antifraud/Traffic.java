package ru.dsi.bgbilling.modules.phone.antifraud;

import java.sql.Date;
import java.sql.Timestamp;

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
     * @param time1 дата начала предыдущей выборки
     * @param time2 дата конца предыдущей выборки
     */
    Traffic(int id, int contract_id, int interzone, int intercity, int international, Date day, int status, Timestamp time1, Timestamp time2) {
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
     * Данные трафика
     *
     * @param contract_id номер контракта
     * @param time1 дата начала предыдущей выборки
     * @param time2 дата конца предыдущей выборки
     * @param day день, за который производится выборка
     */
    public Traffic(int contract_id, Timestamp time1, Timestamp time2, Date day) {
        this.cid = contract_id;
        this.intercity = 0;
        this.international = 0;
        this.interzone = 0;
        this.status = 0;
        this.from = time1;
        this.to = time2;
        this.day = day;
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
    private Timestamp from;

    /**
     * Дата окончания прошлой выборки
     */
    private Timestamp to;

    /**
     * Получить день за котрый производится выборка
     *
     * @return
     */
    public Date getDate() {
        return day;
    }

    /**
     * Установить день за который производится выборка
     *
     * @param day
     */
    public void setDate(Date day) {
        this.day = day;
    }

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

    /**
     * Получение статуса договра. 0 - подключен, 1 - на отключении, 2 -
     * отключен, 3 - закрыт, 4 - приостановлен, 5 - на подключении
     *
     * @return статус договора
     */
    public int getStatus() {
        return status;
    }

    /**
     * Получение даты начала предыдущей выборки
     *
     * @return дата начала предыдущей выборки
     */
    public Timestamp getDateFrom() {
        return from;
    }

    /**
     * Получение даты конца предыдущей выборки
     *
     * @return дата конца предыдущей выборки
     */
    public Timestamp getDateTo() {
        return to;
    }

    /**
     * Установливает статуса договора
     *
     * 0 - подключен, 1 - на отключении, 2 - отключен, 3 - закрыт, 4 -
     * приостановлен, 5 - на подключении
     *
     * @param status статус договора
     */
    public void setStatus(int status) {
        this.status = status;
    }

    /**
     * Устанавливает время разговора по внутризоновой связи
     *
     * @param interzone время разговора по внутризоновой связи
     */
    public void setInterzone(int interzone) {
        this.interzone = interzone;
    }

    /**
     * Устанавливает время разговора по междугородней связи
     *
     * @param intercity время разговора по междугородней связи
     */
    public void setIntercity(int intercity) {
        this.intercity = intercity;
    }

    /**
     * Устанавливает время разговора по международной связи
     *
     * @param international время разговора по международной связи
     */
    public void setInternational(int international) {
        this.international = international;
    }
}
