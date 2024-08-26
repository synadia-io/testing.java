:JsMultiTool_Get_Repo
rd temp /S /Q
md temp
rd java/io/nats/jsmulti /S /Q
cd temp
git clone https://github.com/nats-io/java-nats-examples
cd ..

:JsMultiTool_Remove_Old_Version
git rm src\main\java\io\nats\jsmulti -rf

:JsMultiTool_Copy_Repo_To_Src
xcopy temp\java-nats-examples\js-multi-tool\src\main src\main /S /Y

:JsMultiTool_Remove_Unecessary_Code
rd src\main\java\io\nats\jsmulti\examples /S /Q

:JsMultiTool_Add_Files_To_git
git add src\main\java\io\nats\jsmulti\*

:JsMultiTool_4_Remove_Temp
rd temp /S /Q
