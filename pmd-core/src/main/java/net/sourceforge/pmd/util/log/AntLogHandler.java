/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.apache.tools.ant.BuildListener;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.XmlLogger;
import org.apache.tools.ant.taskdefs.RecorderEntry;

/**
 * AntLogHandler sends log messages to an Ant Task, so the regular Ant logging
 * is used.
 *
 * @author Wouter Zelle
 */
public class AntLogHandler extends Handler {
    private Project project;

    private static final Formatter FORMATTER = new PmdLogFormatter();
    private static final Level[] LOG_LEVELS = {
        Level.SEVERE, Level.WARNING, Level.INFO, Level.CONFIG, Level.FINEST,
    };

    public AntLogHandler(Project project) {
        this.project = project;
    }

    public Level getAntLogLevel() {
        for (final BuildListener l : project.getBuildListeners()) {
            Field declaredField = null;
            try {
                if (l instanceof DefaultLogger) {
                    declaredField = DefaultLogger.class.getDeclaredField("msgOutputLevel");
                } else if (l instanceof XmlLogger) {
                    declaredField = XmlLogger.class.getDeclaredField("msgOutputLevel");
                } else if (l instanceof RecorderEntry) {
                    declaredField = RecorderEntry.class.getDeclaredField("loglevel");
                } else {
                    try {
                        declaredField = l.getClass().getDeclaredField("logLevel");
                    } catch (final NoSuchFieldException e) {
                        project.log("Unsupported build listener: " + l.getClass(), Project.MSG_DEBUG);
                    }
                }

                if (declaredField != null) {
                    declaredField.setAccessible(true);
                    return LOG_LEVELS[declaredField.getInt(l)];
                }
            } catch (final NoSuchFieldException | IllegalArgumentException | IllegalAccessException ignored) {
                // Just ignore it
            }
        }

        project.log("Could not determine ant log level, no supported build listeners found. "
                + "Log level is set to FINEST", Project.MSG_WARN);

        return Level.FINEST;
    }

    @Override
    public void publish(LogRecord logRecord) {
        // Map the log levels from java.util.logging to Ant
        int antLevel;
        Level level = logRecord.getLevel();
        if (level == Level.FINEST) {
            antLevel = Project.MSG_DEBUG; // Shown when -debug is supplied to
            // Ant
        } else if (level == Level.FINE || level == Level.FINER || level == Level.CONFIG) {
            antLevel = Project.MSG_VERBOSE; // Shown when -verbose is supplied
            // to Ant
        } else if (level == Level.INFO) {
            antLevel = Project.MSG_INFO; // Always shown
        } else if (level == Level.WARNING) {
            antLevel = Project.MSG_WARN; // Always shown
        } else if (level == Level.SEVERE) {
            antLevel = Project.MSG_ERR; // Always shown
        } else {
            throw new IllegalStateException("Unknown logging level"); // shouldn't
            // get ALL
            // or NONE
        }

        project.log(FORMATTER.format(logRecord), antLevel);
        if (logRecord.getThrown() != null) {
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter, true);
            logRecord.getThrown().printStackTrace(printWriter);
            project.log(stringWriter.toString(), antLevel);
        }
    }

    @Override
    public void close() throws SecurityException {
        // nothing to do
    }

    @Override
    public void flush() {
        // nothing to do
    }
}
