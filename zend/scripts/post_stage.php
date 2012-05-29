<?php
/*
Copyright (c) 2012, Zend Technologies USA, Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice,
      this list of conditions and the following disclaimer.

    * Redistributions in binary form must reproduce the above copyright notice,
      this list of conditions and the following disclaimer in the documentation
      and/or other materials provided with the distribution.

    * Neither the name of Zend Technologies USA, Inc. nor the names of its
      contributors may be used to endorse or promote products derived from this
      software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

/* The script post_stage.php should contain code that should make the changes in the server
 * environment so that the application is fully functional. For example, this may include
 * changing symbolic links to "data" directories from previous to current versions,
 * upgrading an existing DB schema, or setting up a "Down for Maintenance"
 * message on the live version of the application
 * The following environment variables are accessable to the script:
 * 
 * - ZS_RUN_ONCE_NODE - a Boolean flag stating whether the current node is
 *   flagged to handle "Run Once" actions. In a cluster, this flag will only be set when
 *   the script is executed on once cluster member, which will allow users to write
 *   code that is only executed once per cluster for all different hook scripts. One example
 *   for such code is setting up the database schema or modifying it. In a
 *   single-server setup, this flag will always be set.
 * - ZS_WEBSERVER_TYPE - will contain a code representing the web server type
 *   ("IIS" or "APACHE")
 * - ZS_WEBSERVER_VERSION - will contain the web server version
 * - ZS_WEBSERVER_UID - will contain the web server user id
 * - ZS_WEBSERVER_GID - will contain the web server user group id
 * - ZS_PHP_VERSION - will contain the PHP version Zend Server uses
 * - ZS_APPLICATION_BASE_DIR - will contain the directory to which the deployed
 *   application is staged.
 * - ZS_CURRENT_APP_VERSION - will contain the version number of the application
 *   being installed, as it is specified in the package descriptor file
 * - ZS_PREVIOUS_APP_VERSION - will contain the previous version of the application
 *   being updated, if any. If this is a new installation, this variable will be
 *   empty. This is useful to detect update scenarios and handle upgrades / downgrades
 *   in hook scripts
 * - ZS_<PARAMNAME> - will contain value of parameter defined in deployment.xml, as specified by
 *   user during deployment.
 */

$appLocation = getenv("ZS_APPLICATION_BASE_DIR");
if (!$appLocation) {
	echo "ZS_APPLICATION_BASE_DIR env var undefined" ;
	exit(1);
}
$appVersion = getenv("ZS_CURRENT_APP_VERSION");
if (!$appVersion) {
	echo "ZS_CURRENT_APP_VERSION env var undefined" ;
	exit(1);
}
$appEnv = getenv("ZS_APPLICATION_ENV");
if (!$appEnv) {
	echo "ZS_APPLICATION_ENV env var undefined" ;
	exit(1);
}

file_put_contents("/tmp/deploy.log", "[".date('Ymd H:i:s')."] PostStage: Ver:$appVersion, Env: $appEnv, Run Once: ".getenv('ZS_RUN_ONCE_NODE')."\n", FILE_APPEND);

$htpath = $appLocation.'/public/.htaccess';
$filetxt = file_get_contents($htpath);

if (strpos($filetxt, 'APPLICATION_ENV') === false) {
	$filetxt = "SetEnv APPLICATION_ENV $appEnv\n\n$filetxt";
}
else {
	$filetxt = str_replace('development', $appEnv, $filetxt);
}

file_put_contents($htpath, $filetxt);

$confpath = $appLocation.'/application/configs/application.ini';
$filetxt = file_get_contents($confpath);

$filetxt = str_replace('#version#', $appVersion, $filetxt);

file_put_contents($confpath, $filetxt);

$run = getenv('ZS_RUN_ONCE_NODE');

if ($run) {
	# [Read the configuration] #
	require_once 'Zend/Config/Ini.php';
	$config = new Zend_Config_Ini($confpath, $appEnv);
	
	# [Get the changelog for the current version] #
	$changelogFile = "$appLocation/db/$appVersion/master.xml";
	if(file_exists($changelogFile)) {
		file_put_contents("/tmp/deploy.log", "[".date('Ymd H:i:s')."] DB ChangeLog: $changelogFile\n", FILE_APPEND);
		file_put_contents("/tmp/deploy.log", "[".date('Ymd H:i:s')."] Content: ".file_get_contents($changelogFile)."\n", FILE_APPEND);
		$migrator = new Java('com.zend.db.Migration',
				"mysql",
				$config->resources->db->params->host,
				$config->resources->db->params->dbname,
				$config->resources->db->params->username, 
				$config->resources->db->params->password,
				$changelogFile);
		$migrator->update($appVersion);
	}	
}

echo "Stage Succesful";
exit(0);