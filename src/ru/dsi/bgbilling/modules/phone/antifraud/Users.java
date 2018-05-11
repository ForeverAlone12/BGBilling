package ru.dsi.bgbilling.modules.phone.antifraud;

/**
 * Пользователи которых нельзя блокировать
 *
 * @author
 */
public class Users {

    /**
     * идентификатор записи
     */
    private int id;

    /**
     * номер контракта
     */
    private int cid;

    Users(int id, int cid) {
        this.id = id;
        this.cid = cid;
    }

    /**
     * Получение идентификатора записи
     *
     * @return идентификатор записи
     */
    public int getId() {
        return id;
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
