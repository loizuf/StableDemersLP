package DataStructures;

import nl.tue.geometrycore.geometry.Vector;

import java.util.*;

public class DoubleIntervalTree {

    IntervalTree x_tree, y_tree;

    private class Interval {
        int index;
        Endpoint start, end;

        private Interval(int index, double start, double end){

            this.index = index;
            this.start = new Endpoint(0, start, this);
            this.end = new Endpoint(1, end, this);
        }

        public int contains(Endpoint point) {
            if (point.compareTo(start) < 0) return -1;
            else if (point.compareTo(end) > 0) return 1;
            else return 0;
        }
    }

    private class Endpoint implements Comparable<Endpoint> {
        int type;
        double value;
        Interval interval;

        private Endpoint(int type, double value, Interval interval){

            this.type = type;
            this.value = value;
            this.interval = interval;
        }

        @Override
        public int compareTo(Endpoint o) {
            return Double.compare(value, o.value);
        }

        @Override
        public String toString() {
            return ""+value;
        }
    }

    private class IntervalTree {

        Node root;

        private class Node {
            List<Endpoint> L, R;
            Node left_child, right_child, parent;
            Endpoint position;

            private Node(Endpoint position, Node parent) {
                this.position = position;
                this.parent = parent;
                L = new ArrayList<>();
                R = new ArrayList<>();
            }
        }

        public IntervalTree(Set<Interval> intervals) {
            List<Endpoint> endpoints = new ArrayList<>();
            for (Interval interval : intervals) {
                endpoints.add(interval.start);
                endpoints.add(interval.end);
            }
            endpoints.sort(Endpoint::compareTo);

            root = build(endpoints, null);
        }

        private Node build(List<Endpoint> endpoints, Node parent){
            if (endpoints.size() == 0) return null;
            Endpoint median = endpoints.get((endpoints.size())/2);

            List<Endpoint> to_left = new ArrayList<>();
            List<Endpoint> to_right = new ArrayList<>();
            Node node = new Node(median, parent);

            for (Endpoint endpoint : endpoints) {
                int contains = endpoint.interval.contains(median);
                if (contains < 0) {
                    to_right.add(endpoint);
                } else if (contains > 0) {
                    to_left.add(endpoint);
                } else {
                    if(endpoint.type == 1) {
                     node.R.add(endpoint);
                    } else {
                     node.L.add(endpoint);
                    }
                }
            }
            node.left_child = build(to_left, node);
            node.right_child = build(to_right, node);

            return node;
        }
    }

    public DoubleIntervalTree(Vector[] locations, double[] sizes, int[] indices) {

        Set<Interval> x_intervals = new HashSet<>(), y_intervals = new HashSet<>();
        for (int index : indices) {
            double half_size = sizes[index]/2;
            Vector loc = locations[index];
            double pos_x = loc.getX();
            double pos_y = loc.getY();

            //double[] x_interval = new double[]{pos_x - half_size, pos_x + half_size};
            //double[] y_interval = new double[]{pos_y - half_size, pos_y + half_size};
             Interval x_interval = new Interval(index, pos_x - half_size, pos_x + half_size);
             Interval y_interval = new Interval(index, pos_y - half_size, pos_y + half_size);

            x_intervals.add(x_interval);
            y_intervals.add(y_interval);
        }

        x_tree = new IntervalTree(x_intervals);
        y_tree = new IntervalTree(y_intervals);
    }

    public Set<Integer> crossQuery(Vector loc, double size) {
        Set<Integer> result = query(x_tree.root, loc.getX()-size);
        result.addAll(query(x_tree.root, loc.getX()+size));
        result.addAll(query(y_tree.root, loc.getY()-size));
        result.addAll(query(y_tree.root, loc.getY()+size));
        return result;
    }

    private Set<Integer> query(IntervalTree.Node n, double q) {
        if (n == null) return new HashSet<>();
        else if(q <= n.position.value){
            Set<Integer> result = query(n.left_child, q);
            for (int i = 0; i < n.L.size(); i++) {
                Endpoint endpoint = n.L.get(i);
                if (q >= endpoint.value) result.add(endpoint.interval.index);
                else break;
            }
            return result;
        } else {
            Set<Integer> result = query(n.right_child, q);
            for (int i = n.R.size() - 1; i >= 0; i--) {
                Endpoint endpoint = n.R.get(i);
                if (q <= endpoint.value) result.add(endpoint.interval.index);
                else break;
            }
            return result;
        }
    }

    /*
    public static void main(String[] args) {
        Vector[] locs = new Vector[]{new Vector(1, 1), new Vector(5, 5), new Vector(5, 8), new Vector(8, 5), new Vector(5, 2)};
        double[] sizes = new double[]{2, 2, 2, 2, 2};
        int[] indices = new int[]{0, 1, 2, 3, 4};
        DoubleIntervalTree dt = new DoubleIntervalTree(locs, sizes, indices);

        Set<Integer> result = dt.crossQuery(new Vector(5, 5), 2);
        System.out.println(result);
        //result.forEach(System.out::println);

        //System.out.println(tree.root.left_child.L);
        //System.out.println(tree.root.left_child.R);

        //System.out.println(tree.root.right_child.L);
        //System.out.println(tree.root.right_child.R);

    }
     */
}
