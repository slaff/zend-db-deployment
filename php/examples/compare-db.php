<?php
header('Content-Type: text/xml');

try {
	$migrator = new Java('com.zend.db.Migration',
					    "mysql",
                         "localhost",
                         "zf_development",
                         "root", "",
                         "changelog-4.0.xml");

}
catch(JavaException $ex) {
	print $ex->getMessage();
}


print $migrator->diff("mysql",
                    "localhost",
                    "zf_testing",
                    "root", "");
