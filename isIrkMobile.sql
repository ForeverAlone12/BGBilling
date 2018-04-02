DELIMITER $$
CREATE DEFINER=`root`@`localhost` FUNCTION `isIrkMobile`(`numb` VARCHAR(11)) RETURNS tinyint(1)
    READS SQL DATA
    SQL SECURITY INVOKER
    COMMENT 'Проверка принадлежит ли номер сотовым операторам Иркутской области'
BEGIN
RETURN (SELECT Count(*)
FROM (SELECT id_mobilephone, code, `start`, `end`, 
	IF (SUBSTR(numb,2,3) like code AND SUBSTR(numb,5)
	BETWEEN `start` AND `end`, 1,0)  as sta
	FROM mobilephone
	HAVING sta = 1
	LIMIT 0,63) rez);
END$$
DELIMITER ;