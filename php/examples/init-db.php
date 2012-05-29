<?php

header('Content-Type: text/xml');

try {
	$migrator = Java('com.zend.db.Migration',
					    "mysql",
                         "localhost",
                         "zf_development",
                         "root", "",
                         "changelog-4.0.xml");

}
catch(JavaException $ex) {
	print $ex->getMessage();
}


print $migrator->init("slaff");
