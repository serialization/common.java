package user;

import age.Age;
import age.internal.SkillState;
import de.ust.skill.common.java.api.SkillFile.Mode;

public class Main {
    public static void main(String[] args) throws Exception {
        {
            SkillState sf = SkillState.open("age-test.sf", Mode.Create);
            for (Age a : sf.Ages())
                System.out.println(a.prettyString());
            System.out.println("check!");
        }
        {
            SkillState sf = SkillState.open("test/ageUnrestricted.sf", Mode.Read);
            for (Age a : sf.Ages())
                System.out.println(a.prettyString());
            System.out.println("check!");
        }
    }
}
