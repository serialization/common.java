package age.internal;

import java.util.Collections;
import java.util.HashSet;

import de.ust.skill.common.java.api.SkillException;
import de.ust.skill.common.java.api.SkillFile.Mode;
import de.ust.skill.common.java.internal.BasePool;
import de.ust.skill.common.java.internal.ParseException;
import de.ust.skill.common.java.internal.SkillObject;
import de.ust.skill.common.java.internal.StoragePool;
import de.ust.skill.common.java.restrictions.TypeRestriction;
import de.ust.skill.common.jvm.streams.FileInputStream;

final public class FileParser extends de.ust.skill.common.java.internal.FileParser<SkillState> {

    public final SkillState state;

    /**
     * Constructs a parser that parses the file from in and constructs the
     * state. State is valid immediately after construction.
     */
    private FileParser(FileInputStream in, Mode writeMode) throws ParseException {
        super(in);

        // parse blocks
        while (!in.eof()) {
            stringBlock();
            typeBlock();
        }

        this.state = makeState(writeMode);
    }

    /**
     * turns a file into a state.
     * 
     * @note this method is abstract, because some methods, including state
     *       allocation depend on the specification
     */
    public static SkillState read(FileInputStream in, Mode writeMode) throws ParseException {
        FileParser p = new FileParser(in, writeMode);
        return p.state;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T extends B, B extends SkillObject> StoragePool<T, B> newPool(String name,
            StoragePool<? super T, B> superPool, HashSet<TypeRestriction> restrictions) {
        final StoragePool<T, B> p;
        // allocate correct pool type
        switch (name) {
        case "age":
            p = (StoragePool<T, B>) new AgeAccess(types.size());
            break;

        default:
            if (null == superPool)
                p = (StoragePool<T, B>) new BasePool<T>(types.size(), name, Collections.EMPTY_SET);
            else
                p = superPool.makeSubPool(types.size(), name);
            break;
        }

        // check super type expectations
        if (p.superPool() != superPool)
            throw new ParseException(
                    in,
                    blockCounter,
                    null,
                    "The super type of %s stored in the file does not match the specification!\nexpected %s, but was %s",
                    name, null == p.superPool() ? "<none>" : p.superPool().name(), null == superPool ? "<none>"
                            : superPool.name());

        types.add(p);
        poolByName.put(name, p);

        return p;
    }

    private SkillState makeState(Mode mode) {
        SkillState r = new SkillState(poolByName, Strings, types, in.path(), mode);
        try {
            r.check();
        } catch (SkillException e) {
            throw new ParseException(in, blockCounter, e, "Post serialization check failed!");
        }
        return r;
    }
}
