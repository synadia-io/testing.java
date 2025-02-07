if ("%1"=="") goto gen

copy generator%1.json generator.json

:gen

java -cp "build/libs/*" io.synadia.utils.Generator full
