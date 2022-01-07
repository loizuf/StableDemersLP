package Main;

import java.util.*;

public class Tarjan {

    private static int index;
    private static Stack<Vertex> stack;
    private static List<Set<Integer>> result;
    private static Map<Vertex, Set<Vertex>> adjacencies;

    protected static class Vertex {
        protected int name, index = -1, lowlink = -1;
        protected boolean onStack = false;

        protected Vertex(int name) {
            this.name = name;
        }
    }

    public static List<Set<Integer>> getStrongConnectedComponents(Map<Integer, Set<Integer>> stabilities) {

        index = 0;
        stack = new Stack<>();
        result = new ArrayList<>();
        adjacencies = new HashMap<>();

        // This just builds the vertices and edges
        ArrayList<Vertex> vertices = new ArrayList<>();
        Map<Integer, Vertex> tempMap = new HashMap<>();
        for (Integer name : stabilities.keySet()) {
            Vertex v = new Vertex(name);
            vertices.add(v);
            tempMap.put(name, v);
        }
        for (Vertex vertex : vertices) {
            HashSet<Vertex> tempSet = new HashSet<>();
            for (Integer integer : stabilities.get(vertex.name)) {
                tempSet.add(tempMap.get(integer));
            }
            adjacencies.put(vertex, tempSet);
        }
        // End edge building
        // dont use tempMap and tempList anymore!

        for (Vertex v : vertices) {
            if(v.index == -1) {
                strongConnect(v);
            }
        }

        return result;
    }

    private static void strongConnect(Vertex v) {
        v.index = index;
        v.lowlink = index;
        index++;

        stack.push(v);
        v.onStack = true;

        for (Vertex w : adjacencies.get(v)) {
            if(w.index == -1) {
                strongConnect(w);
                v.lowlink = Math.min(v.lowlink, w.lowlink);
            } else if(w.onStack) {
                v.lowlink = Math.min(v.lowlink, w.index);
            }
        }

        if (v.lowlink == v.index) {
            Set<Integer> component = new HashSet<>();
            Vertex w;
            do{
                w = stack.pop();
                w.onStack = false;
                component.add(w.name);
            } while (v != w);

            result.add(component);
        }
    }
}
