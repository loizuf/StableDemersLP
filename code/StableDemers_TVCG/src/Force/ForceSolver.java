package Force;

import DataStructures.DoubleIntervalTree;
import GUI.DrawPanel;
import GUI.MySidePanel;
import Main.RunResults;
import Main.Settings;
import Model.CartogramModel;
import Model.Region;
import com.google.common.collect.Sets;
import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.geometry.curved.Circle;
import nl.tue.geometrycore.geometry.linear.Line;
import nl.tue.geometrycore.geometry.linear.Rectangle;
import nl.tue.geometrycore.geometryrendering.GeometryPanel;
import nl.tue.geometrycore.gui.GUIUtil;
import nl.tue.geometrycore.gui.sidepanel.TabbedSidePanel;
import nl.tue.geometrycore.util.DoubleUtil;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class ForceSolver extends Main.Solver {

    private CartogramModel cartogram;

    private double maxdist;

    //HashMap<Integer, HashMap<Integer, Integer>> force_index_to_index;
    //HashMap<Integer, HashMap<Integer, Integer>> index_to_force_index;
    private Vector[][] layer_index_forces;
    private Vector[][] layer_index_output_locations;
    //Region[][] force_regions;
    private double[][] sizes;
    private int[][] indices;

    boolean use_cross = true;
    boolean use_grid = true;

    List<Double> max_sizes;

    private double[] minimal_distance;
    public static HashMap<Integer, HashMap<Integer, List<Vector>>> lastDisjointForces;
    public static HashMap<Integer, HashMap<Integer, List<Vector>>> lastMapForces;
    public static HashMap<Integer, HashMap<Integer, Vector>> lastTotalForces;
    private HashMap<Integer, HashMap<Integer, Double>> region_factors;
//    private GeometryPanel panel;
    private Circle[] points;

    private boolean first = true;

    int nowLayer = 0;
    private DrawPanel dp;
    private MySidePanel sp;


    private void setup(CartogramModel c){
        points = new Circle[Settings.Force.max_iterations];

        /* Strictly DEBUG */


        dp = new DrawPanel(c);
        sp = new MySidePanel(c.getLayerCount(), dp);
        dp.setSP(sp);
//        IPEWriter write = null;

        if(Settings.GUI) {
            String title = " - " + Settings.solution_method;
            if(Settings.solution_method.equals("LP")) {

            } else {
                title += "(" + Settings.Force.speed_up;
                if(Settings.Force.topological_f){
                    title += " - T";
                }
                if(Settings.Force.origin_f){
                    title += " - O";
                }
                if(Settings.Force.stability_f){
                    title += " - S";
                }
                title += ")";
            }
            GUIUtil.makeMainFrame(title, dp, sp);
            sp.repaint();
            dp.repaint();
        }

        /* Strictly DEBUG */


//        this.panel = new GeometryPanel() {
//            @Override
//            protected void drawScene() {
//                setStroke(Color.black, .4, null);
//                draw(new Line(new Vector(0, 0), new Vector(0, 1000)));
//                setStroke(Color.red, .2, null);
//                draw(new Line(new Vector(0, 1000*Settings.Force.minimal_movement * c.getMinSize(nowLayer)), new Vector(10000, 1000*Settings.Force.minimal_movement * c.getMinSize(nowLayer))));
//
//                setStroke(new Color(75, 98, 121), .2, null);
//                setFill(new Color(75, 98, 121), null);
//                for (Circle point : points) {
//                    draw(point);
//                }
//            }
//
//            @Override
//            public Rectangle getBoundingRectangle() {
//                return null;
//            }
//
//            @Override
//            protected void mousePress(Vector vector, int i, boolean b, boolean b1, boolean b2) {
//                System.out.println(vector);
//            }
//
//            @Override
//            protected void keyPress(int i, boolean b, boolean b1, boolean b2) {
//                System.out.println(i);
//            }
//        };
//        GUIUtil.makeMainFrame("jha", panel, new TabbedSidePanel());
        this.cartogram = c;

        lastDisjointForces = new HashMap<>();
        lastMapForces = new HashMap<>();
        lastTotalForces = new HashMap<>();
        region_factors = new HashMap<>();

        switch (Settings.Force.speed_up) {
            case "cross":
                use_cross = true;
                use_grid = false;
                break;
            case "grid":
                use_cross = false;
                use_grid = true;
                max_sizes = new ArrayList<>();
                for (int i = 0; i < c.getLayerCount(); i++) {
                    max_sizes.add(c.getMaxSize(i));
                }
                break;
            default:
                use_cross = false;
                use_grid = false;
                break;
        }

        //force_index_to_index = new HashMap<>();
        //index_to_force_index = new HashMap<>();
        layer_index_forces = new Vector[cartogram.getLayerCount()][];
        layer_index_output_locations = new Vector[cartogram.getLayerCount()][];
        sizes = new double[cartogram.getLayerCount()][];
        indices = new int[cartogram.getLayerCount()][];
        minimal_distance = new double[cartogram.getLayerCount()];
        //force_regions = new Region[cartogram.getLayerCount()][];

        for (int layer = 0; layer < cartogram.getLayerCount(); layer++) {
            lastDisjointForces.put(layer, new HashMap<>());
            lastMapForces.put(layer, new HashMap<>());
            lastTotalForces.put(layer, new HashMap<>());
            region_factors.put(layer, new HashMap<>());
            layer_index_forces[layer] = new Vector[cartogram.getRegionCount()];
            layer_index_output_locations[layer] = new Vector[cartogram.getRegionCount()];
            //force_index_to_index.put(layer, new HashMap<>());
            //index_to_force_index.put(layer, new HashMap<>());
            sizes[layer] = new double[cartogram.getRegionCount()];

            List<Region> regions = cartogram.getRegions(layer);
            indices[layer] = new int[regions.size()];
            //force_regions[layer] = new Region[regions.size()];
            for (int i = 0; i < regions.size(); i++) {
                Region layer_region = regions.get(i);
                //force_index_to_index.get(layer).put(i, layer_region.index);
                //index_to_force_index.get(layer).put(layer_region.index, i);
                layer_index_forces[layer][layer_region.index] = Vector.origin().clone();
                // If we switch to other systems of base spots again, this is where we would initialize different values for the output locations
                layer_index_output_locations[layer][layer_region.index] = layer_region.getOriginalLocation().clone();




                sizes[layer][layer_region.index] = layer_region.getSize(layer);
                indices[layer][i] = layer_region.index;
                //force_regions[layer][i] = layer_region;
                lastDisjointForces.get(layer).put(layer_region.index, new ArrayList<>());
                lastMapForces.get(layer).put(layer_region.index, new ArrayList<>());
                lastTotalForces.get(layer).put(layer_region.index, null);
                region_factors.get(layer).put(layer_region.index, 1.0);
            }

            double layer_min, layer_max;
            layer_min = c.getMinSize(layer);
            layer_max = c.getMaxSize(layer);
            minimal_distance[layer] = Math.min(layer_min, (layer_max-layer_min)/10);
        }
        maxdist = cartogram.getInputBoundingBox().width() + cartogram.getInputBoundingBox().height();
//        System.out.println(maxdist);
//        in();

    }

    private void in() {
        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    boolean output = true;
    @Override
    public RunResults run(CartogramModel c, List<Set<Integer>> iterative_order) throws IOException {

        setup(c);
//        System.out.println(iterative_order);

        for (int i = 0; i < cartogram.getLayerCount(); i++) {
            System.out.println("i: " + cartogram.getNumberOfStableLayers(i));
        }

        RunResults results = new RunResults();

        boolean didsomething = false;
        double[] max_force_length = new double[cartogram.getLayerCount()];
//        double[] avg_force_length = new double[cartogram.getLayerCount()];

        Map<Integer, Set<Integer>> stabilities = c.getStabilities();
        for (int j = 0; j < iterative_order.size(); j++) {
            Set<Integer> current_layers = iterative_order.get(j);
            int counter = 0;
            double start = System.currentTimeMillis();

            //Assign positions of previously solved layer to this one.

            if(current_layers.size() == 1) {
                Integer current_layer = current_layers.iterator().next();
                System.out.println(stabilities.get(current_layer));
                if(stabilities.get(current_layer).size() > 0){
                    int stable_to = stabilities.get(current_layer).iterator().next();
                    List<Region> previous_regions = c.getRegions(stable_to);
                    for (Region region : c.getRegions(current_layer)) {
                        if(previous_regions.contains(region)) {
                            layer_index_output_locations[current_layer][region.index] = region.getPosition(stable_to).clone();
                        } else {
                            layer_index_output_locations[current_layer][region.index] = region.getOriginalLocation().clone();
                        }
                    }
                }
            }

//            if (j>0 && ){
//                Set<Integer> previous_layers = iterative_order.get(j-1);
//                if(previous_layers.size() == 1){
//
//                }
//                for (Integer current_layer : current_layers) {
//                    for (Region region : c.getRegions(current_layer)) {
//                        for (Integer previous_layer : previous_layers) {
//
//                        }
//                    }
//                }
//            }

//            System.out.println("iteartions total: "+Settings.Force.max_iterations);
//            System.out.println("counter: "+counter);
                while (counter < Settings.Force.max_iterations) {
                    didsomething = false;
                    if (counter > 0) {
                        output = false;
                        first = false;
                    } else {
                        first = true;
                    }
                    // compute forces
                    for (Integer layer : current_layers) {
//                    if(layer == 0) continue;

                        List<Region> regions = cartogram.getRegions(layer);
                        DoubleIntervalTree dt = null;


                        //Int keys are k*gird_width + l
                        HashMap<Integer, HashMap<Integer, Set<Integer>>> cells = new HashMap<>();
                        HashMap<Integer, Set<String>> index_to_cells = new HashMap<>();

                        if (use_cross) {
                            dt = new DoubleIntervalTree(layer_index_output_locations[layer], sizes[layer], indices[layer]);
                        } else if (use_grid) {
                            double l_min_x = Double.MAX_VALUE, l_max_x = -Double.MAX_VALUE, l_min_y = Double.MAX_VALUE, l_max_y = -Double.MAX_VALUE;
                            for (int i : indices[layer]) {
                                double current_x = layer_index_output_locations[layer][i].getX();
                                double current_y = layer_index_output_locations[layer][i].getY();
                                if (current_x > l_max_x) l_max_x = current_x;
                                if (current_x < l_min_x) l_min_x = current_x;
                                if (current_y > l_max_y) l_max_y = current_y;
                                if (current_y < l_min_y) l_min_y = current_y;
                            }
                            //Vector grid_origin = new Vector(l_min_x, l_min_y);
                            double cell_size = max_sizes.get(layer) + minimal_distance[layer];
                            double left_edge = l_min_x - cell_size / 2;
                            double right_edge = l_max_x + cell_size / 2;
                            double lower_edge = l_min_y - cell_size / 2;
                            //double upper_edge = l_max_y + cell_size/2;
                            int grid_width = (int) ((right_edge - left_edge) / cell_size) + 5;
                            //int grid_height = (int) ((lower_edge - upper_edge + 1) / cell_size);

                            for (Region layer_region : regions) {
                                int i = layer_region.index;
                                double offset = (sizes[layer][i] + minimal_distance[layer]) / 2;
                                Vector current_positions = layer_index_output_locations[layer][i];
                                double left = current_positions.getX() - offset;
                                double right = current_positions.getX() + offset;
                                double bottom = current_positions.getY() - offset;
                                double top = current_positions.getY() + offset;

                                int x_start = (int) ((left - left_edge) / cell_size);
                                int x_end = (int) ((right - left_edge) / cell_size);
                                int y_start = (int) ((bottom - lower_edge) / cell_size);
                                int y_end = (int) ((top - lower_edge) / cell_size);

//                            if(output && (layer_region.code.equals("CA")||layer_region.code.equals("CT")))
//                                System.out.println("Region " + i + " from x: [" + x_start + ", " + x_end + "] and z: [" + y_start + ", " + y_end + "]");

                                // gridsize is bigger than a single region, therefore region should never occupy more than 4 cells
//                            if(x_end > x_start + 1) {
//                                System.out.println("Somethings wrong: " + x_start + ", " + x_end);
//                                System.out.println(left);
//                                System.out.println(right);
//                                System.out.println(left_edge);
//                                System.out.println(cell_size);
//                                System.out.println();
//                            }
//                            if(y_end > y_start + 1) System.out.println("Somethings wrong");

                                index_to_cells.put(i, new HashSet<>());

                                //int key = x_start + y_start*grid_width;

                                // Initialize grid cells if necessary and add id
                                if (!cells.containsKey(x_start)) cells.put(x_start, new HashMap<>());
                                if (!cells.get(x_start).containsKey(y_start))
                                    cells.get(x_start).put(y_start, new HashSet<>());
                                cells.get(x_start).get(y_start).add(i);
                                index_to_cells.get(i).add(x_start + "," + y_start);

                                if (y_end != y_start) {
                                    if (!cells.get(x_start).containsKey(y_end))
                                        cells.get(x_start).put(y_end, new HashSet<>());
                                    cells.get(x_start).get(y_end).add(i);
                                    index_to_cells.get(i).add(x_start + "," + y_end);
                                }

                                if (x_end != x_start) {
                                    if (!cells.containsKey(x_end)) cells.put(x_end, new HashMap<>());
                                    if (!cells.get(x_end).containsKey(y_start))
                                        cells.get(x_end).put(y_start, new HashSet<>());
                                    cells.get(x_end).get(y_start).add(i);
                                    index_to_cells.get(i).add(x_end + "," + y_start);
                                    if (y_end != y_start) {
                                        if (!cells.get(x_end).containsKey(y_end))
                                            cells.get(x_end).put(y_end, new HashSet<>());
                                        cells.get(x_end).get(y_end).add(i);
                                        index_to_cells.get(i).add(x_end + "," + y_end);
                                    }
                                }
                                // End initialization

//                            if(!cells.get(()).containsKey(y_start)) cells.put(y_start, new HashMap<>());
//                            if(!cells.containsKey(y_end)) cells.put(y_end, new HashMap<>());
//
//                            if(!cells.containsKey(key)) cells.put(key, new HashSet<>());
//                            cells.get(key).add(i);
//                            index_to_cells.get(i).add(key);
//
//                            if (x_end != x_start) {
//                                key = x_end + y_start*grid_width;
//                                if(!cells.containsKey(key)) cells.put(key, new HashSet<>());
//                                cells.get(key).add(i);
//                                index_to_cells.get(i).add(key);
//                            }
//                            if (y_end != y_start) {
//                                key = x_start + y_end*grid_width;
//                                if(!cells.containsKey(key)) cells.put(key, new HashSet<>());
//                                cells.get(key).add(i);
//                                index_to_cells.get(i).add(key);
//                            }
//                            if (x_end != x_start && y_end != y_start) {
//                                key = x_end + y_end*grid_width;
//                                if(!cells.containsKey(key)) cells.put(key, new HashSet<>());
//                                cells.get(key).add(i);
//                                index_to_cells.get(i).add(key);
//                            }
                            }
                        }

                        for (int i = 0; i < regions.size(); i++) {
                            Region region = regions.get(i);

                            Set<Integer> others = new HashSet<>();
                            if (use_cross) {
                                others = dt.crossQuery(layer_index_output_locations[layer][region.index], region.getSize(layer) / 2);
                            } else if (use_grid) {
                                for (String region_cell : index_to_cells.get(region.index)) {
                                    String[] tok = region_cell.split(",");
                                    int row = Integer.parseInt(tok[0]);
                                    int column = Integer.parseInt(tok[1]);
                                    others.addAll(cells.get(row).get(column));
                                }
                            } else {
                                for (Region value : regions) {
                                    others.add(value.index);
                                }
                            }
                            //System.out.println(others);

                            Vector disjoint_force = Vector.origin();
                            if (Settings.Force.disjoint_f) {
                                disjoint_force = applyDisjointForceSelective(layer, region, others);
//                            if(use_cross || use_grid){
//                                disjoint_force = applyDisjointForceSelective(layer, region, others);
//                            } else {
//                                disjoint_force = applyDisjointForce(layer, region);
//                            }
//                            System.out.println(disjoint_force.length());

                                if (counter > Settings.Force.max_iterations / 10) {
                                    disjoint_force.scale(Settings.Force.disjoint_weight * (10 * region.getSize(layer) / cartogram.getMaxSize(layer)));
                                }
//                            System.out.println(disjoint_force.length());
//                            System.out.println();
//                            System.in.read();
//                            if(first) {
//                                lastDisjointForces.get(layer).get(region.index).add(0, disjoint_force);
//                            }
                                lastDisjointForces.get(layer).get(region.index).add(0, disjoint_force);
                                if (region.code.equals("GM0200")) {
//                                System.out.println(region.code + " disj force length: " + disjoint_force.length());
                                }
                            }

                            Vector layer_force = Vector.origin();
                            if (Settings.Force.origin_f) {
                                layer_force = applyOriginForce(layer, region);

                                if (counter < Settings.Force.max_iterations / 10) {
                                    layer_force.scale(30*Settings.Force.origin_weight / maxdist);
                                } else {
                                    layer_force.scale(Settings.Force.origin_weight / maxdist);
                                }
//                            if(first) {
//                                lastMapForces.get(layer).get(region.index).add(0, layer_force);
//                            }
                                lastMapForces.get(layer).get(region.index).add(0, layer_force);
                                if (region.code.equals("GM0200")) {
//                                System.out.println(region.code + " origin force length: " + layer_force.length());
                                }
                            }
                            if (Settings.Force.topological_f) {
                                layer_force = applyTopoForce(layer, region);
                                layer_force.scale(Settings.Force.topological_weight);
//                            if(first) {
//                                lastMapForces.get(layer).get(region.index).add(0, layer_force);
//                            }
                                if (region.code.equals("GM0200")) {
//                                System.out.println(region.code + " topo force length: " + layer_force.length());
                                }
//                            double factor = 1.0;
//                            if(counter < Settings.Force.max_iterations/2){
//                                if(lastMapForces.get(layer).get(region.index).size() > 0){
//                                    Vector previous = lastMapForces.get(layer).get(region.index).get(0);
//                                    double angle = Math.acos(Vector.dotProduct(layer_force, previous) / (layer_force.length() * previous.length()));
//                                    if(angle > 5.84685 || angle < 0.436332){
//                                        region_factors.get(layer).put(region.index, region_factors.get(layer).get(region.index)*1.01);
//                                    } else {
//                                        region_factors.get(layer).put(region.index, 1.0);
//                                    }
//                                }
//                                lastMapForces.get(layer).get(region.index).add(0, layer_force);
//                                layer_force.scale(region_factors.get(layer).get(region.index));
//                            }
                            }

                            Vector stability_force = Vector.origin();
                            if (Settings.Force.stability_f) {
                                stability_force = applyStabilityForce(layer, region);
                                stability_force.scale(Settings.Force.stability_weight);
//                            System.out.println("stab force length: " + stability_force.length());
                            }

                            int additional_forces = 0;
                            if (Settings.Force.origin_f) additional_forces++;
                            if (Settings.Force.topological_f) additional_forces++;
                            if (Settings.Force.stability_f) additional_forces++;

                            // This vector is in range [0, Settings.Force.disjoint_weight + additional_forces]
//                        double alpha = 5*(double)(Settings.Force.max_iterations - counter)/Settings.Force.max_iterations;
//                        layer_force.scale(alpha);


                            layer_index_forces[layer][region.index] = Vector.add(disjoint_force, layer_force);
                            if (Settings.Force.stability_f)
                                layer_index_forces[layer][region.index] = Vector.add(layer_index_forces[layer][region.index], stability_force);
                            // Scale it to [0, 1]
//                            if(counter%100 == 0)
//                                System.out.println(((counter - Settings.Force.max_iterations / 10.0)/(Settings.Force.max_iterations / 5.0)));
                            if (disjoint_force.length() > 0) {
                                if (counter > Settings.Force.max_iterations / 10.0) {
                                    layer_index_forces[layer][region.index].scale(Math.max(10, Math.pow((counter - Settings.Force.max_iterations / 10.0)/(Settings.Force.max_iterations / 5.0), 2)) / (Settings.Force.disjoint_weight + additional_forces));
                                }
                            }
//                        layer_index_forces[layer][region.index].scale((10*c.getRegionCount(layer)) / (maxdist));
                            layer_index_forces[layer][region.index].scale(Math.exp(-((double) counter / Settings.Force.max_iterations)));

//                        layer_index_forces[layer][region.index].normalize();

//                        System.out.println(region.code + " :: " + layer_index_forces[layer][region.index] + " |||| " + layer_index_forces[layer][region.index].length());

//                        if(layer_index_forces[layer][region.index].length() > 1){
//                            System.out.println("----------------");
//                            System.out.println("disj: " + disjoint_force);
//                            System.out.println("layer: " + layer_force);
//                            System.out.println("sum: " + Vector.add(disjoint_force, layer_force));
//                            System.out.println("saved: " + layer_index_forces[layer][region.index]);
//                            System.out.println("----------------");
//                        }

//                        layer_index_forces[layer][region.index].scale(Math.exp(-(1.0/Settings.Force.max_iterations) * counter) / (Settings.Force.disjoint_weight + additional_forces));
//                        double length_here = layer_index_forces[layer][region.index].length();
//                        System.out.println("here: " + Math.log(length_here+10));
//                        layer_index_forces[layer][region.index].scale(length_here * length_here);
//                        layer_index_forces[layer][region.index].scale(Math.exp(-(1.0/Settings.Force.max_iterations) * counter));
                            // Scale it further to [0, 0.25 * cartogram.getMinSize()]
//                        System.out.println();
//                        layer_index_forces[layer][region.index].scale();
//                        layer_index_forces[layer][region.index].scale(maxdist);

//                        if(counter% 100 == 0 && region.code.equals("GM0928")){
//                            System.out.println("----------------");
//                            System.out.println("disj: " + disjoint_force);
//                            System.out.println("layer: " + layer_force);
//                            System.out.println("sum: " + Vector.add(disjoint_force, layer_force));
//                            System.out.println("saved: " + layer_index_forces[layer][region.index]);
//                            System.out.println("----------------");
//                        }
                            if (first) {

                            }
                            lastTotalForces.get(layer).put(region.index, layer_index_forces[layer][region.index]);
                        }
//                    System.in.read();

                        max_force_length[layer] = 0;
//                    avg_force_length[layer] = 0;
                        for (Region region : cartogram.getRegions(layer)) {
                            Vector single_force = layer_index_forces[layer][region.index];
                            if (single_force.length() >
                                    max_force_length[layer]) {
                                max_force_length[layer] = single_force.length();
                            }
                        }

//                    if(max_force_length[layer] > 1) {
//                        for (Region region : cartogram.getRegions(layer)) {
//                            layer_index_forces[layer][region.index].scale(1 / max_force_length[layer]);
//                        }
//                    }

//
//                    for (Region region : cartogram.getRegions(layer)) {
//                        Vector single_force = layer_index_forces[layer][region.index];
//                        single_force.scale(1/(max_force_length[layer]));
//                    }
                    }


                    // apply forces
                    for (Integer layer : current_layers) {
                        List<Region> regions = cartogram.getRegions(layer);
                        for (int i = 0; i < regions.size(); i++) {
                            Region region = regions.get(i);
//                        layer_index_output_locations[layer][region.index] = Vector.add(layer_index_output_locations[layer][region.index], layer_index_forces[layer][region.index]);
//                        System.out.println("ratio of length of movement to length of location vector: "+ layer_index_forces[layer][region.index].length()/layer_index_output_locations[layer][region.index].length());
//                        System.out.println("ratio of length of movement to size of region: "+ layer_index_forces[layer][region.index].length()/region.getSize(layer));
                            layer_index_output_locations[layer][region.index].translate(layer_index_forces[layer][region.index]);
                            double force_length = layer_index_forces[layer][region.index].length();
//                        avg_force_length[layer] += force_length;
//                        System.out.println("length: "+force_length);
                            if (force_length > Settings.Force.minimal_movement * c.getMinSize(layer)) {
//                            if (force_length > max_force_length[layer])
//                                if(force_length>10000){
//                                    System.out.println(force_length);
//                                }
//                                max_force_length[layer] = force_length;
                                didsomething = true;
                            }
                        }
                    }
//                System.in.read();

                    // DEBUG
                    for (int layer = 0; layer < cartogram.getLayerCount(); layer++) {
                        //if (avg_force_length[layer]/cartogram.getRegions(layer).size() > Settings.Force.minimal_movement * c.getAverageSize(layer)) didsomething = true;
                    }
                    // DEBUG

                    if (!didsomething) {
                        System.out.println("Layer(s) " + current_layers + " solved!\n");
                        break;
                    }

                    // DEBUG
                    for (Integer i : current_layers) {
                        points[counter] = new Circle(new Vector(counter, 1000 * max_force_length[i]), 1);
                        nowLayer = i;
                    }
//                panel.repaintNow();

                    if (counter % 1000 == 0) {
                        System.out.println("Its remaining: " + (Settings.Force.max_iterations - counter));
                        //System.out.println("Biggest Force is at " + max_force_length);
                        //System.out.println(Settings.Force.minimal_movement * c.getMinSize());
                        for (Integer i : current_layers) {

                            if (max_force_length[i] > Settings.Force.minimal_movement * c.getMinSize(i)) {
//                        if(max_force_length[i]>Settings.Force.minimal_movement) {
                                System.out.println("Layer " + i + " gap: " + ((max_force_length[i]) - (Settings.Force.minimal_movement * c.getMinSize(i))));
//                            System.out.println("Longest Force is: " +(max_force_length[i]));
                            } else {
                                System.out.println("Layer " + i + " fine.");
                            }

//                        if(avg_force_length[i]/cartogram.getRegions(i).size()>Settings.Force.minimal_movement * c.getAverageSize(i)) {
                            //System.out.println("Layer " + i + " gap: " + ((avg_force_length[i]/cartogram.getRegions(i).size()) - (Settings.Force.minimal_movement * c.getAverageSize(i))));
//                        } else {
                            //System.out.println("Layer " + i + " fine.");
//                        }
                            //System.out.println("Average Force for layer " + i + " is at " + avg_force_length[i]/cartogram.getRegions(i).size());
                            //System.out.println(Settings.Force.minimal_movement * c.getAverageSize(i));
                        }
                    }
                    // DEBUG

                    //System.out.println(output_locations);
                    counter += 1;

                    /*DEBUG ADD POSITIONS*/

                    for (Integer current_layer : current_layers) {
                        HashMap<Integer, Vector> upPos = new HashMap<>();
                        Vector[] layer_index_output_location = layer_index_output_locations[current_layer];
                        for (Region region : cartogram.getRegions(current_layer)) {
                            upPos.put(region.index, layer_index_output_location[region.index]);
                        }
                        c.updateRegionPositions(current_layer, upPos);
                    }
                    dp.repaint();
                }
            results.addRuntime(System.currentTimeMillis() - start);
            results.addIterations(counter);

        }


        HashMap<Integer, HashMap<Integer, Vector>> output_locations = new HashMap<>();

        for (int layer = 0; layer < layer_index_output_locations.length; layer++) {
            HashMap<Integer, Vector> layer_output_locations = new HashMap<>();
            for (Region region : cartogram.getRegions(layer)) {
                layer_output_locations.put(region.index, layer_index_output_locations[layer][region.index]);
            }
            output_locations.put(layer, layer_output_locations);
        }

        results.addPositions(output_locations);


        // Note that our Force-directed approach does not have the concept of variable size placeholder regions

        return results;
    }

    private Vector applyDisjointForceSelective(int layer, Region r, Set<Integer> counter_parts) {
        //System.out.println("calling disjoint with id: " + id + " and layer: "+ layer);
        //System.out.println(outputLocations.size());

        Vector loc_r = layer_index_output_locations[layer][r.index];
        Vector disjoint_force = Vector.origin();
        int counter = 0;
        lastDisjointForces.get(layer).put(r.index, new ArrayList<>());

        for (Integer j : counter_parts) {
            Region s = cartogram.getRegion(j);
            if (r.index == s.index) continue;
            Vector loc_s = layer_index_output_locations[layer][s.index];
            boolean adjacent = cartogram.adjacent(r, s, layer);

            Vector diff = Vector.subtract(loc_r, loc_s);
            if (diff.isApproximately(Vector.origin())) {
                diff.translate(0.0001, 0.0001);
            }
            double Linf = Math.max(Math.abs(diff.getX()), Math.abs(diff.getY()));
            double target = (r.getSize(layer) + s.getSize(layer)) / 2.0 + (adjacent ? 0 : minimal_distance[layer]);
//            double target = (r.getSize(layer) + s.getSize(layer)) / 2.0;

            if (Linf < target) {

                if(r.code.equals("GM0200")) {
//                    System.out.print(s.code + ", ");
                }
                counter++;
                double d = (target - Linf) / target;
//                System.out.println(d);
//                double d = (target - Linf) ;
                diff.normalize();
                diff.scale(d*d);
//                diff.scale(d);
                disjoint_force.translate(diff);
                lastDisjointForces.get(layer).get(r.index).add(diff);
            }
        }
//        if(output) {
//            System.out.println("Applied force for region " + r.code + " " + counter + " times");
//        }
        if(counter>0){
            disjoint_force.scale(1.0 / counter);
        }
        return disjoint_force;
    }

//    private Vector applyDisjointForce(int layer, Region r) {
//        //System.out.println("calling disjoint with id: " + id + " and layer: "+ layer);
//        //System.out.println(outputLocations.size());
//
//        Vector loc_r = layer_index_output_locations[layer][r.index];
//        Vector disjoint_force = Vector.origin();
//        List<Region> regions = cartogram.getRegions(layer);
//        //System.out.println("Region " + r.code + " list is " + regions.toString());
//        int counter = 0;
//        for (int j = 0; j < regions.size(); j++) {
//            Region s = regions.get(j);
//            if (r.index == s.index) continue;
//            Vector loc_s = layer_index_output_locations[layer][s.index];
//            boolean adjacent = cartogram.adjacent(r, s, layer);
//
//            Vector diff = Vector.subtract(loc_r, loc_s);
//            double Linf = Math.max(Math.abs(diff.getX()), Math.abs(diff.getY()));
//            double target = (r.getSize(layer) + s.getSize(layer)) / 2.0 + (adjacent ? 0 : minimal_distance[layer]);
//            if (Linf < target) {
//
////                if(output && r.code.equals("CA")) {
////                    System.out.print(s.code + ", ");
////                }
//                counter++;
//                double d = (target - Linf) / target;
//                if (diff.isApproximately(Vector.origin())) {
//                    diff.translate(0.0001, 0.0001);
//                }
//                diff.normalize();
//                diff.scale(d * d);
//                disjoint_force.translate(diff);
//            }
//        }
//        if(output) {
//            System.out.println("Applied force for region " + r.code + " " + counter + " times");
//        }
//        return disjoint_force;
//    }

    private Vector applyOriginForce(int layer, Region r) {

        Vector or = r.getOriginalLocation();
        Vector loc = layer_index_output_locations[layer][r.index];

//        System.out.println("__________");
//        System.out.println(or);
//        System.out.println(loc);

        Vector diff = Vector.subtract(or, loc);
        double Linf = Math.max(Math.abs(diff.getX()), Math.abs(diff.getY()));
//        if (diff.isApproximately(Vector.origin())) {
//            diff.translate(0.0001, 0.0001);
//        }
//        System.out.println(Linf);
        if(Linf == 0) {
            return diff;
        }
//        System.out.println(diff);
        diff.normalize();
//        System.out.println(diff);
        diff.scale(Math.sqrt(Linf));
//        System.out.println(diff);
//        System.out.println("----------------");
        lastMapForces.get(layer).put(r.index, new ArrayList<>());
        lastMapForces.get(layer).get(r.index).add(diff);
//        diff.scale(1.0 / maxdist);
//        try {
//            System.in.read();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        return diff;
    }

    private Vector applyTopoForce(int layer, Region r) {

        Vector topological_force = Vector.origin();

        lastMapForces.get(layer).put(r.index, new ArrayList<>());

        Vector loc_r = layer_index_output_locations[layer][r.index];
        Set<Region> neighbours = cartogram.getNeighbours(r, layer);
        if (neighbours.size() != 0) {
            for (Region s : neighbours) {
                //int s_force_index = index_to_force_index.get(layer).get(s.index);
                Vector loc_s = layer_index_output_locations[layer][s.index];

                Vector diff = Vector.subtract(loc_s, loc_r);
                double Linf = Math.max(Math.abs(diff.getX()), Math.abs(diff.getY()));
                if(r.code.equals("GM1883") && s.code.equals("GM1711")){
//                    System.out.println(Linf);
                }
                double target = (r.getSize(layer) + s.getSize(layer))/2.0;
                //System.out.println(layer + "|"+r.code +": "+ diff + ", " +Linf);

//                double tolerance = (r.getSize(layer) + s.getSize(layer))/10;
                double d;
                if(Linf  > target) {
                    d = (Linf - target)/target;
//                    System.out.println(d);
                    double origLength = (Linf - target);
                    diff.normalize();
                    if (diff.isApproximately(Vector.origin())) {
                        diff.translate(0.0001, 0.0001);
                    }
//                    diff.scale(d);
                    diff.scale(Math.max(1,Math.sqrt(origLength)));
//                    diff.scale(d * d);
                    topological_force.translate(diff);
                    lastMapForces.get(layer).get(r.index).add(diff);
                }

            }
        }
//        if(topological_force.length() > 100 && layer == 1){
//            System.out.println(topological_force.length());
//        }
//        topological_force.scale(1.0 / (neighbours.size()*maxdist));
        topological_force.scale(1.0 / (neighbours.size()));
        return topological_force;
    }

    private Vector applyStabilityForce(int layer, Region r) {
        Vector loc_r = layer_index_output_locations[layer][r.index];
        Vector f = Vector.origin();
        for (int l = 0; l < cartogram.getLayerCount(); l++) {
            if (l == layer || !cartogram.isStableTo(layer, l) || !cartogram.isPresent(r, l))
                continue;
            Vector loc_s = layer_index_output_locations[l][r.index];
            Vector diff = Vector.subtract(loc_r, loc_s);
            f = Vector.add(diff, f);
        }
        f.scale(1.0 / (maxdist*cartogram.getNumberOfStableLayers(layer)));
        return f;
    }

}
