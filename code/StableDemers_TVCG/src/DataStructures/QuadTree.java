package DataStructures;

/*
 Code copied and adapted from:
 https://algs4.cs.princeton.edu/92search/QuadTree.java.html
 */


import nl.tue.geometrycore.geometry.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/******************************************************************************
 *  Compilation:  javac QuadTree.java
 *  Execution:    java QuadTree M N
 *
 *  Quad tree.
 *
 ******************************************************************************/

public class QuadTree {
    private Node root;
    private Map<Integer, Node> id_to_node;
    private Map<Node, Integer> node_to_id;

    // helper node data type
    private class Node {
        double min_x, max_x, min_y, max_y;              // x- and y- coordinates
        Node NW;
        Node NE;
        Node SE;
        Node SW;
        Node parent;
        Map<Integer, Vector> vectors;

        Node(double min_x, double max_x, double min_y, double max_y, Node parent) {
            this.min_x = min_x;
            this.max_x = max_x;
            this.min_y = min_y;
            this.max_y = max_y;
            this.parent = parent;
            vectors = new HashMap<>();
        }
    }

    public QuadTree(Map<Integer, Vector> vectors, double min_x, double max_x, double min_y, double max_y){
        id_to_node = new HashMap<>();
        node_to_id = new HashMap<>();
        root = build(null, vectors, min_x, max_x, min_y, max_y);
    }

    private Node build(Node parent, Map<Integer, Vector> vectors, double min_x, double max_x, double min_y, double max_y) {
        Node node = new Node(min_x, max_x, min_y, max_y, parent);
        node.vectors.putAll(vectors);
        if(node.vectors.size() > 1) {
            // split node
            double mid_x = (node.max_x - node.min_x) / 2;
            double mid_y = (node.max_y - node.min_y) / 2;
            Map<Integer, Vector> nw = new HashMap<Integer, Vector>(), ne = new HashMap<Integer, Vector>(), sw = new HashMap<Integer, Vector>(), se = new HashMap<Integer, Vector>();
            for (Map.Entry<Integer, Vector> entry : node.vectors.entrySet()) {
                if(entry.getValue().getX() <= mid_x && entry.getValue().getY() > mid_y) nw.put(entry.getKey(), entry.getValue());
                if(entry.getValue().getX() > mid_x && entry.getValue().getY() > mid_y) ne.put(entry.getKey(), entry.getValue());
                if(entry.getValue().getX() <= mid_x && entry.getValue().getY() <= mid_y) sw.put(entry.getKey(), entry.getValue());
                if(entry.getValue().getX() > mid_x && entry.getValue().getY() <= mid_y) se.put(entry.getKey(), entry.getValue());
            }
            node.NW = build(node, nw, node.min_x,   mid_x,      mid_y,      node.max_y  );
            node.NE = build(node, ne, mid_x,        node.max_x, mid_y,      node.max_y  );
            node.SW = build(node, sw, node.min_x,   mid_x,      node.min_y, mid_y       );
            node.SE = build(node, se, mid_x,        node.max_x, node.min_y, mid_y       );
        } else if(node.vectors.size() == 1) {
            // just one
            for (Integer id : node.vectors.keySet()) {
                id_to_node.put(id, node);
                node_to_id.put(node, id);
            }
        }
        return node;
    }

    public Set<Node> getNeighbours(Node node) {
        Set<Node> neighbours = new HashSet<>();
        Node parent = node.parent;
        if(parent == null) return neighbours;
        if(parent.NW != node) neighbours.add(parent.NW);
        if(parent.NE != node) neighbours.add(parent.NE);
        if(parent.SW != node) neighbours.add(parent.SW);
        if(parent.SE != node) neighbours.add(parent.SE);
        return neighbours;
    }

    private void print(Node node){
        System.out.println(node.vectors);

        if(node.NW != null){
            System.out.println("->NW");
            print(node.NW);
            System.out.println("->NE");
            print(node.NE);
            System.out.println("->SW");
            print(node.SW);
            System.out.println("->SE");
            print(node.SE);
        }
    }

    public static void main(String[] args) {
        Map<Integer, Vector> vs = new HashMap<>();
        vs.put(1, new Vector(1, 1));
        vs.put(2, new Vector(4, 4));
        vs.put(3, new Vector(1, 4));
        vs.put(4, new Vector(4, 1));

        QuadTree t = new QuadTree(vs, 0, 10, 0, 10);
        //t.print(t.root);

//        System.out.println(t.id_to_node);
    }

}
