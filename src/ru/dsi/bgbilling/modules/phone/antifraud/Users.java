package ru.dsi.bgbilling.modules.phone.antifraud;

/**
 * Пользователи которых нельзя блокировать
 *
 * @author
 */
public class Users {

    /**
     * номер договора
     */
    private int cid;

    private int international;

    private int intercity;

    private int interzone;

    Users(int cid) {
        this.cid = cid;
    }

 

    /**
     * Получение номера контракта
     *
     * @return номер контракта
     */
    public int getCid() {
        return cid;
    }

}
