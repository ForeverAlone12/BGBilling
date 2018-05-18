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

    Users() {
        this.cid = 0;
        this.intercity = 0;
        this.international = 0;
        this.interzone = 0;
    }

    Users(int cid, int international, int intercity, int interzone) {
        this.cid = cid;
        this.intercity = intercity;
        this.international = international;
        this.interzone = interzone;
    }

    Users(int cid) {
        this.cid = cid;
        this.intercity = 0;
        this.international = 0;
        this.interzone = 0;
    }

    /**
     * Получение номера контракта
     *
     * @return номер контракта
     */
    public int getCid() {
        return cid;
    }

    /**
     * @param cid the cid to set
     */
    public void setCid(int cid) {
        this.cid = cid;
    }

    /**
     * @return the international
     */
    public int getInternational() {
        return international;
    }

    /**
     * @param international the international to set
     */
    public void setInternational(int international) {
        this.international = international;
    }

    /**
     * @return the intercity
     */
    public int getIntercity() {
        return intercity;
    }

    /**
     * @param intercity the intercity to set
     */
    public void setIntercity(int intercity) {
        this.intercity = intercity;
    }

    /**
     * @return the interzone
     */
    public int getInterzone() {
        return interzone;
    }

    /**
     * @param interzone the interzone to set
     */
    public void setInterzone(int interzone) {
        this.interzone = interzone;
    }

}
