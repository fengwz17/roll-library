/**
 * Zielonka Tree of a Muller Condition
 */
/**
 * @author weizhi feng
 *
 */

package roll.learner.dpa.zielonka;

// import roll.words.Alphabet;
import java.util.ArrayList;
import java.util.List;
 
public class ZielonkaTree {

	// input F, S(parent)
	// output S1,S2,...Sk be the maximal subsets of S 
	// Si \in F <=> S \notin F
	public List<List<Integer>> getChildren(List<List<Integer>> setF, List<Integer> setS){
		List<List<Integer>> childrenSets = new ArrayList<>();
		List<List<Integer>> subSets = getSubsets(setS);

		// S \notin F
		if(!isElement(setF, setS))
		{
			for(int i = 0; i < setF.size(); i++)
			{
				// subset of S in F
				if(setS.containsAll(setF.get(i)))
				{
					childrenSets.add(setF.get(i));
				}
			}
		}

		// S \in F
		else
		{
			for(int i = 0; i < subSets.size(); i++)
			{
				// subset of S \notin F
				if(!isElement(setF, subSets.get(i)))
				{
					childrenSets.add(subSets.get(i));
				}
			}
		}
		
		// remove small subsets
		for(int j = 0; j < childrenSets.size(); j++)
		{
			for(int k = j + 1; k < childrenSets.size(); k++)
			{
				if(childrenSets.get(j).containsAll(childrenSets.get(k)))
				{
					childrenSets.remove(childrenSets.get(k));
				}
				if(childrenSets.get(k).containsAll(childrenSets.get(j)))
				{
					childrenSets.remove(childrenSets.get(j));
				}
			}
		}
		return childrenSets;
	}

	// input F, Si
	// output true if Si \in F
	public boolean isElement(List<List<Integer>> setF, List<Integer> setS){
		
		for(int i = 0; i < setF.size(); i++)
		{
			List<Integer> subSet = setF.get(i);
			if(subSet.size() == setS.size())
			{
				if(subSet.containsAll(setS) && setS.containsAll(subSet))
				{
					return true;
				}
				return false;
			}
		}
		return false;
	}


	// get subset
	public List<List<Integer>> getSubsets(List<Integer> setS) {
        List<List<Integer>> allSubsets = new ArrayList<>();

		// 2^n
        int size = 1 << setS.size();
        for(int m = 0; m < size; m++) 
		{
            List<Integer> subSet = new ArrayList<>();
            for(int i = 0; i < setS.size(); i++) 
			{    
                if((m & (1 << i)) != 0) 
				{
					subSet.add(setS.get(i));
				}
			}
			if (!subSet.isEmpty())
			{
				allSubsets.add(subSet);
			}
        }
        return allSubsets;
    }

}	

