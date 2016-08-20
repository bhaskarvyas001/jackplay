package jackplay.play;

import jackplay.Logger;
import jackplay.bootstrap.Genre;
import static jackplay.bootstrap.Genre.*;
import jackplay.bootstrap.Options;
import jackplay.bootstrap.PlayGround;
import jackplay.play.performers.LeadPerformer;
import jackplay.play.performers.Performer;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PlayCoordinator {
    Instrumentation inst;
    ProgramManager pm;
    Options options;
    InfoCenter infoCenter;
    LeadPerformer leadPerformer;

    public void init(Instrumentation inst, Options options, ProgramManager pm, InfoCenter infoCenter, LeadPerformer leadPerformer) {
        this.inst = inst;
        this.pm = pm;
        this.options = options;
        this.infoCenter = infoCenter;
        this.leadPerformer = leadPerformer;
        this.inst.addTransformer(leadPerformer, true);
    }

    private boolean verifyPlayability(Method method) throws PlayException {
        String packageName = method.getDeclaringClass().getPackage().getName();
        if (!options.packageAllowed(packageName)) {
            throw new InvalidPlayGroundException("package not allowed: " + packageName);
        }

        String className = method.getDeclaringClass().getCanonicalName();
        if (!infoCenter.hasMethodBody(method)) {
            throw new InvalidPlayGroundException("method has no body (either native or abstract): " +
                    className + "." + method.getName());
        }

        if (!inst.isModifiableClass(method.getDeclaringClass())) throw new InvalidPlayGroundException("class not modifiable:" + className);

        if (!inst.isRetransformClassesSupported()) throw new PlayException("RetransformClass not supported");

        return true;
    }

    public void handleRetransformationError(Throwable t, Class clazz, Genre genre, PlayGround pg) throws PlayException {
        // if an agenda causes problem, we do our best by removing it and
        // re-transform with this agenda removed - in other words, undo it

        Logger.error(t);
        pm.removeAgenda(genre, pg);
        try {
            inst.retransformClasses(clazz);
        } catch(Exception bestEffort) {}

        throw new PlayException("An " + t.getClass().getName() + " error occurred while attempting to retransform "
                + pg.classFullName + ": " + t.getMessage());
    }

    private synchronized void play(Genre genre, PlayGround pg, String newBody) throws PlayException {
        if (pm.addAgenda(genre, pg, newBody)) {
            boolean matched = false;
            List<Class> loadedMatchedClasses = infoCenter.findLoadedClasses(pg.classFullName);
            for (Class clazz : loadedMatchedClasses) {
                Method method = infoCenter.findMatchingMethod(clazz, pg);
                if (method != null && verifyPlayability(method)) {
                    try {
                        matched = true;
                        inst.retransformClasses(clazz);
                    } catch (Throwable t) {
                        handleRetransformationError(t, clazz, genre, pg);
                    }
                }
            }

            if (!matched) {
                Logger.debug("agenda " + genre + ", " + pg.methodFullName + " added but not played yet");
            }
        }
    }

    private synchronized void undoPlay(Genre genre, PlayGround pg) throws PlayException {
        if (pm.removeAgenda(genre, pg)) {
            List<Class> loadedMatchedClasses = infoCenter.findLoadedClasses(pg.classFullName);
            for (Class clazz : loadedMatchedClasses) {
                try {
                    inst.retransformClasses(clazz);
                } catch (Throwable t) {
                    throw new PlayException("An " + t.getClass().getName() + " error occurred while attempting to retransform "
                            + pg.classFullName + ": " + t.getMessage());
                }
            }
        }
    }

    public void trace(PlayGround pg) throws PlayException {
        this.play(METHOD_TRACE, pg, null);
    }

    public void undoTrace(PlayGround pg) throws PlayException {
        this.undoPlay(METHOD_TRACE, pg);
    }

    public void redefine(PlayGround pg, String newBody) throws PlayException {
        this.play(METHOD_REDEFINE, pg, newBody);
    }

    public void undoRedefine(PlayGround pg) throws PlayException {
        this.undoPlay(METHOD_REDEFINE, pg);
    }

    public void undoClass(Genre genre, String className) throws PlayException {
        Map<String, ?> plays = pm.program.get(genre).get(className);
        if (!plays.isEmpty()) {
            for (String methodFullName : plays.keySet()) {
                this.undoPlay(genre, new PlayGround(methodFullName));
            }
        }
    }

    public void undoAll(Genre genre) throws PlayException {
        Map<String,?> traces = pm.program.get(genre);
        if (!traces.isEmpty()) {
            for (String className : traces.keySet()) {
                this.undoClass(genre, className);
            }
        }
    }

    public void undoAll() throws PlayException {
        this.undoAll(METHOD_TRACE);
        this.undoAll(METHOD_REDEFINE);
    }

    public Map<Genre, Map<String, Map<String, Performer>>> getCurrentProgram() {
        return Collections.unmodifiableMap(pm.program);
    }
}
