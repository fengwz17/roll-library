/**
 * use Zielonka Tree to learn DPA
 */
/**
 * @author weizhi feng
 *
 */

package roll.learner.dpa.zielonka;

import roll.automata.DPA;
import roll.words.Alphabet;

// import roll.learner.LearnerBase;
// import roll.learner.LearnerType;
// import roll.main.Options;
// import roll.oracle.MembershipOracle;
// import roll.query.Query;
// import roll.table.HashableValue;
// import roll.util.Timer;



public class LearnerDPAZielonkaTree {

    	Alphabet alphabet = new Alphabet();
	    DPA A = new DPA(alphabet);
		A.createState();
		A.createState();
		// A.getState(0).addTransition(0, 0);
		// A.getState(0).addTransition(1, 1);
		// A.getState(1).addTransition(1, 1);
		// A.setFinal(1);
		// A.setInitial(0);

}

