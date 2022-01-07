package Main;

import Model.CartogramModel;

import java.util.List;
import java.util.Set;

public abstract class Solver {

    public RunResults buildAndRun(CartogramModel c) throws  Exception{
        List<Set<Integer>> iterative_order = Tarjan.getStrongConnectedComponents(c.getStabilities());
        return run(c, iterative_order);
    }

    public abstract RunResults run(CartogramModel c, List<Set<Integer>> iterative_order) throws Exception;
}
