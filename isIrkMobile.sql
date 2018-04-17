DROP FUNCTION IF EXISTS is_irk_mobile;
CREATE DEFINER=`bill`@`%` FUNCTION `is_irk_mobile`(`numb` VARCHAR(11)) 
RETURNS tinyint(1)
    LANGUAGE SQL
    DETERMINISTIC
    READS SQL DATA
    SQL SECURITY DEFINER
    COMMENT 'Проверка принадлежит ли номер сотовым операторам Иркутской области'
	/* Вернём 1, если найдётся в таблице mobilephone такая строка,
	где подстрока с 2 по 4 символ совпадёт с полем code и подстрока
	с 5 по 11 символ - между началом и концом диапазона. Иначе вернём 0 */ 
RETURN  IF(EXISTS
		(SELECT 1 
	    FROM mobilephone
        WHERE SUBSTR(numb, 2, 3) = `code` 
        AND SUBSTR(numb, 5, 7) BETWEEN `start` AND `end`),
    1,
    0);
