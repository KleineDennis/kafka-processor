Logging
=======

All application logging is sent to `stdout` in JSON format, configured via the standard [logback.xml](./conf/logback.xml) configuration.
Inside AWS, the [stack.yml](./deploy/stack.yaml) cloudformation template redirects `stdout` output to an `application.log` file inside the EC2 instances, tailed by `fluentd` and
shipped to Kinesis (eventually forwarded to Kibana). 

Note that the application log level to `stdout` can be different than the log-level read by `fluentd`. 

## How to log inside application 

1. Inject `infrastructure.app.LoggerProvider` in your class
2. Instantiate a logger instance: `val logger = loggerProvider.get(getClass)` 
3. Optionally configure logback log level inside [logback.xml](./conf/logback.xml) for this particular class via `<logger name="<CLASS_PATH_HERE>" level="<SPECIFIC_LOG_LEVEL>" />`
4. Log something: `logger.info("foo")`

### Typed logs

You can log content w/ structure easily using the [TypedLogs](../app/infrastructure/app/TypedLog.scala) class.

1. Inside `Object TypedLog {...}` add case classes/objects that extend the `TypedLog` trait.
2. The parameters of the case class will be automatically mapped to key-value JSON pairs in the final log
3. Simply use `logger.info(TypedLogExample)` (or any other log-level) to log the TypedLogs (remember to import `infrastructure.app.TypedLog.LoggerEnhancer` for implicit conversion to work where you make the log call)

Note: All `TypedLog` logs need to have a message, if not specified it will default to the class name in snake-case

### Note on log-level

Logback follows regular syslog conventions and provides a log-level for every log entry (unlike the old `eventPublisher` library)
Use this to your advantage when searching for logs in Kibana or creating visualizations.

