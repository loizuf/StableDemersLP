package LP;

import Main.RunResults;
import Main.Settings;
import Model.CartogramModel;
import Model.Region;
import ilog.concert.*;
import ilog.cplex.IloCplex;
import jdk.jfr.StackTrace;
import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.util.DoubleUtil;

import java.util.*;

public class CartogramCplex extends IloCplex {

    private IloNumExpr objective;
    private boolean solved;

    public boolean use_big_m = true;

    /*
     * one per layer. While the lists will have the length of all layer and we will use the layer as index in the list,
     * we only save information about the relevant layers in this list!
     */
    private HashMap<Integer, HashMap<Integer, IloNumVar>> $x_coord;
    private HashMap<Integer, HashMap<Integer, IloNumVar>> $y_coord;
    private HashMap<Integer, HashMap<Integer, IloNumVar>> $widths;
    private HashMap<Integer, HashMap<Integer, HashMap<Integer, IloIntVar[]>>> $direction_choosers;
    private HashMap<Integer, HashMap<Integer, HashMap<Integer, IloNumVar>>> $adjacency_counters;
    private HashMap<Integer, HashMap<Integer, HashMap<Integer, IloNumVar[]>>> $adjacency_minimizers;
    private HashMap<Integer, HashMap<Integer, HashMap<Integer, IloNumVar>>> $nuance_minimizers;
    private HashMap<Integer, HashMap<Integer, IloNumVar[]>> $origin_minimizers;
    private HashMap<Integer, HashMap<Integer, HashMap<Integer, IloNumVar[]>>> $stability_minimizers;

    private Set<Integer> layers;

    // These values are used to normalize constraint objective function values
    private double distance_weight;
    private double nuance_weight;
    private double topology_weight;
    private double origin_weight;
    private double stability_weight;

    /**
     * Constructor of CartogramCplex object
     * @param layers Set of layers solved by this program
     * @throws IloException
     */
    public CartogramCplex(Set<Integer> layers) throws IloException {
        super();
        this.layers = new HashSet<>(layers);
        this.solved = false;
        objective = this.linearNumExpr();
    }

    /**
     * This function sets up the weights for the objective function, by scaling them for number of instances and the range of the minimized values
     * @param total_region_count sum of the numbers of regions in each layer
     * @param total_adjacent_region_count sum of the numbers of adjacent region pairs in each layer (not double counted)
     * @param total_stability_count sum of inter-layer region stabilities
     * @param input_bounding_box_size size of the input bounding box. Used as a rough estimate of the range for constraints, which measure actual distances
     * @param max_size
     */
    public void setupObjectiveFunctionWeights(int total_region_count, int total_squared_region_count, int total_adjacent_region_count, int total_stability_count, double input_bounding_box_size, int layer_count, double max_size) {

//        if(layers.contains(1)){
//            System.out.println();
//            System.out.println("-------------------------");
//            System.out.println("total_region_count = " + total_region_count + "\ntotal_squared_region_count = " + total_squared_region_count + "\ntotal_adjacent_region_count = " + total_adjacent_region_count + "\ntotal_stability_count = " + total_stability_count + "\ninput_bounding_box_size = " + input_bounding_box_size + "\nlayer_count = " + layer_count);
//            System.out.println("-------------------------");
//            System.out.println();
//        }

        distance_weight = Settings.LP.factor_dist_min / (input_bounding_box_size * total_adjacent_region_count);
        nuance_weight = Settings.LP.factor_angle_nuance / (max_size * total_squared_region_count);
//        topology_weight = Settings.LP.factor_topology/ (total_adjacent_region_count);
        topology_weight = Settings.LP.factor_topology;
        origin_weight = Settings.LP.factor_orig_displ / (input_bounding_box_size * total_region_count);
//        if(total_stability_count == 0) {
//            System.out.println(total_stability_count);
//            stability_weight = 0;
//        }
//        else stability_weight = Settings.LP.factor_stability / (input_bounding_box_size * total_stability_count);
        stability_weight = Settings.LP.factor_stability  / (input_bounding_box_size * total_stability_count);


//        System.out.print("D: " + distance_weight);
//        System.out.print(", N: " + nuance_weight);
//        System.out.print(", T: " + topology_weight);
//        System.out.print(", O: " + origin_weight);
//        System.out.println(", S: " + stability_weight);

    }

    public void setObjective() throws IloException {
//        System.out.println(objective);
        this.addMinimize(objective);
    }

    public void setSolved(boolean runSuccess) {
        this.solved = runSuccess;
    }

    public boolean isSolved() {
        return solved;
    }

    public void setXCoords(HashMap<Integer, HashMap<Integer, IloNumVar>> $x_coord) {
        this.$x_coord = $x_coord;
//        System.out.println("X-Coords set");
    }

    public HashMap<Integer, HashMap<Integer, IloNumVar>> get$x_coord() {
        return $x_coord;
    }

    public void setYCoords(HashMap<Integer, HashMap<Integer, IloNumVar>> $y_coord) {
        this.$y_coord = $y_coord;
    }

    public HashMap<Integer, HashMap<Integer, IloNumVar>> get$y_coord() {
        return $y_coord;
    }

    public HashMap<Integer, HashMap<Integer, IloNumVar>> get$widths() {
        return $widths;
    }

    public HashMap<Integer, Vector> getRegionPositions(Integer layer) throws IloException {
        HashMap<Integer,Vector> positions = new HashMap<>();
        HashMap<Integer, IloNumVar> xs = $x_coord.get(layer);
        HashMap<Integer, IloNumVar> ys = $y_coord.get(layer);
        for (Integer id : xs.keySet()){
            try{
                positions.put(id, new Vector(getValue(xs.get(id)), getValue(ys.get(id))));
            } catch (Exception e) {
                System.out.println(e + ":" + xs.get(id) + " was not found in the program.");
                System.out.println(ys.get(id) + " was not found in the program.");
            }
        }
        return positions;
    }

    public HashMap<Integer, Double> getObstacleSizes(Integer layer) throws IloException {
        HashMap<Integer, Double> sizes = new HashMap<>();
        HashMap<Integer, IloNumVar> widths = $widths.get(layer);
        for (Integer id : widths.keySet()){
            try{
                sizes.put(id, getValue(widths.get(id)));
            } catch (Exception e) {
                System.out.println(widths.get(id) + " was not found in the program.");
            }
        }
        return sizes;
    }

    public void setWidths(HashMap<Integer, HashMap<Integer, IloNumVar>> $widths) {
        this.$widths = $widths;
    }

    public void setDirectionChoosers(HashMap<Integer, HashMap<Integer, HashMap<Integer, IloIntVar[]>>> $direction_chooser) {
        this.$direction_choosers = $direction_chooser;
    }

    public void setAdjacencyCounters(HashMap<Integer, HashMap<Integer, HashMap<Integer, IloNumVar>>> $adjacencyCounters) {
        this.$adjacency_counters = $adjacencyCounters;
    }

    public ArrayList<IloNumVar> getAdjacencyCounters(int layer) {
        Collection<HashMap<Integer, IloNumVar>> temp = $adjacency_counters.get(layer).values();
        ArrayList<IloNumVar> vars = new ArrayList<>();
        for (HashMap<Integer, IloNumVar> map : temp) {
            vars.addAll(map.values());
        }
        return vars;
    }

    public void setAdjacencyMinimizers(HashMap<Integer, HashMap<Integer, HashMap<Integer, IloNumVar[]>>> $adjacencyMinimizers) {
        this.$adjacency_minimizers = $adjacencyMinimizers;
    }

    public ArrayList<IloNumVar[]> getAdjacencyMinimizers(int layer) {
        Collection<HashMap<Integer, IloNumVar[]>> temp = $adjacency_minimizers.get(layer).values();
        ArrayList<IloNumVar[]> vars = new ArrayList<>();
        for (HashMap<Integer, IloNumVar[]> map : temp) {
            vars.addAll(map.values());
        }
        return vars;
    }

    public void setNuanceMinimizers(HashMap<Integer, HashMap<Integer, HashMap<Integer, IloNumVar>>> nuance_minimizers) {
        this.$nuance_minimizers = nuance_minimizers;
    }

    public void setOriginMinimizers(HashMap<Integer, HashMap<Integer, IloNumVar[]>> origin_minimizers) {
        this.$origin_minimizers = origin_minimizers;
    }

    public void setStabilityMinimizers(HashMap<Integer, HashMap<Integer, HashMap<Integer, IloNumVar[]>>> stability_minimizers) {
        this.$stability_minimizers = stability_minimizers;
    }

    public Set<Integer> getLayers() {
        return layers;
    }

    /* START CONSTRAINTS */

    public void addILPDisjointness(Region r, Region s, Integer layer, double gap, String direction) throws IloException {

        IloNumExpr distance1 = sum(prod(-1, $y_coord.get(layer).get(r.index)), $y_coord.get(layer).get(s.index));
        IloNumExpr distance2 = sum($y_coord.get(layer).get(r.index), prod(-1, $y_coord.get(layer).get(s.index)));
        IloNumExpr distance3 = sum(prod(-1, $x_coord.get(layer).get(r.index)), $x_coord.get(layer).get(s.index));
        IloNumExpr distance4 = sum($x_coord.get(layer).get(r.index), prod(-1, $x_coord.get(layer).get(s.index)));



        IloNumExpr distance_below = sum(prod(-1, $y_coord.get(layer).get(r.index)), $y_coord.get(layer).get(s.index));
        IloNumExpr distance_above = sum($y_coord.get(layer).get(r.index), prod(-1, $y_coord.get(layer).get(s.index)));
        IloNumExpr distance_left = sum(prod(-1, $x_coord.get(layer).get(r.index)), $x_coord.get(layer).get(s.index));
        IloNumExpr distance_right = sum($x_coord.get(layer).get(r.index), prod(-1, $x_coord.get(layer).get(s.index)));

//        IloIntExpr switch1 = sum(1, prod(-1, $direction_choosers.get(layer).get(r.index).get(s.index)[0]));
//        IloIntExpr switch2 = sum(1, prod(-1, $direction_choosers.get(layer).get(r.index).get(s.index)[1]));
//        IloIntExpr switch3 = sum(1, prod(-1, $direction_choosers.get(layer).get(r.index).get(s.index)[2]));
//        IloIntExpr switch4 = sum(1, prod(-1, $direction_choosers.get(layer).get(r.index).get(s.index)[3]));

        IloIntVar switch_below = $direction_choosers.get(layer).get(r.index).get(s.index)[0];
        IloIntVar switch_above = $direction_choosers.get(layer).get(r.index).get(s.index)[1];
        IloIntVar switch_left = $direction_choosers.get(layer).get(r.index).get(s.index)[2];
        IloIntVar switch_right = $direction_choosers.get(layer).get(r.index).get(s.index)[3];

        if (r.isObstacle) {
            if (s.isObstacle){
                IloNumExpr dist = sum(gap, prod(0.5, sum($widths.get(layer).get(r.index), $widths.get(layer).get(s.index))));
                add(ifThen(eq(switch_below, 1), ge(distance_below,dist)));
                add(ifThen(eq(switch_above, 1), ge(distance_above,dist)));
                add(ifThen(eq(switch_left, 1), ge(distance_left,dist)));
                add(ifThen(eq(switch_right, 1), ge(distance_right,dist)));
//                add(ge(distance1, sum(dist, prod(switch1, -Settings.LP.big_constant)))); //below
//                add(ge(distance2, sum(dist, prod(switch2, -Settings.LP.big_constant)))); //above
//                add(ge(distance3, sum(dist, prod(switch3, -Settings.LP.big_constant)))); //left
//                add(ge(distance4, sum(dist, prod(switch4, -Settings.LP.big_constant)))); //right
            } else {
                IloNumExpr dist = sum(gap, prod(0.5, sum($widths.get(layer).get(r.index), s.getSize(layer))));
                add(ifThen(eq(switch_below, 1), ge(distance_below,dist)));
                add(ifThen(eq(switch_above, 1), ge(distance_above,dist)));
                add(ifThen(eq(switch_left, 1), ge(distance_left,dist)));
                add(ifThen(eq(switch_right, 1), ge(distance_right,dist)));
//                add(ge(distance1, sum(dist, prod(switch1, -Settings.LP.big_constant)))); //below
//                add(ge(distance2, sum(dist, prod(switch2, -Settings.LP.big_constant)))); //above
//                add(ge(distance3, sum(dist, prod(switch3, -Settings.LP.big_constant)))); //left
//                add(ge(distance4, sum(dist, prod(switch4, -Settings.LP.big_constant)))); //right
            }
        } else {
            if (s.isObstacle) {
                IloNumExpr dist = sum(gap, prod(0.5, sum(r.getSize(layer), $widths.get(layer).get(s.index))));
                add(ifThen(eq(switch_below, 1), ge(distance_below,dist)));
                add(ifThen(eq(switch_above, 1), ge(distance_above,dist)));
                add(ifThen(eq(switch_left, 1), ge(distance_left,dist)));
                add(ifThen(eq(switch_right, 1), ge(distance_right,dist)));
//                add(ge(distance1, sum(dist, prod(switch1, -Settings.LP.big_constant)))); //below
//                add(ge(distance2, sum(dist, prod(switch2, -Settings.LP.big_constant)))); //above
//                add(ge(distance3, sum(dist, prod(switch3, -Settings.LP.big_constant)))); //left
//                add(ge(distance4, sum(dist, prod(switch4, -Settings.LP.big_constant)))); //right
            } else {
                double dist = gap + 0.5 * (r.getSize(layer) + s.getSize(layer));
                add(ifThen(eq(switch_below, 1), ge(distance_below,dist)));
                add(ifThen(eq(switch_above, 1), ge(distance_above,dist)));
                add(ifThen(eq(switch_left, 1), ge(distance_left,dist)));
                add(ifThen(eq(switch_right, 1), ge(distance_right,dist)));
//                add(ge(distance1, sum(dist, prod(switch1, -Settings.LP.big_constant)))); //below
//                add(ge(distance2, sum(dist, prod(switch2, -Settings.LP.big_constant)))); //above
//                add(ge(distance3, sum(dist, prod(switch3, -Settings.LP.big_constant)))); //left
//                add(ge(distance4, sum(dist, prod(switch4, -Settings.LP.big_constant)))); //right
            }
        }

        // At least a single switch must be true
        add(ge(
                sum(
                        sum(switch_above, switch_below),
                        sum(switch_left, switch_right)
                ), 1
        ));


        // Setting initial values of the variables to their natural directions. It is not clear if this will speed up the process.
        double[] initial_directions = new double[4];
        switch(direction.toLowerCase().trim()){
            case "below":
                initial_directions[0] = 1;
                break;
            case "above":
                initial_directions[1] = 1;
                break;
            case "left":
                initial_directions[2] = 1;
                break;
            case "right":
                initial_directions[3] = 1;
                break;
            default:
                System.out.println(String.format("The given direction %s, interpreted as %s is not a valid option. Please choose 'below', 'right', 'above' or 'left'.", direction, direction.toLowerCase().trim()));
                break;
        }
        changeMIPStart(0, new IloIntVar[]{switch_above, switch_below, switch_left, switch_right}, initial_directions);
//        add(ge(
//                sum(
//                        sum($direction_choosers.get(layer).get(r.index).get(s.index)[0], $direction_choosers.get(layer).get(r.index).get(s.index)[1]),
//                        sum($direction_choosers.get(layer).get(r.index).get(s.index)[2], $direction_choosers.get(layer).get(r.index).get(s.index)[3])
//                ), 1
//        ));
    }

    /**
     * Adds a hard constraint to the program which ensures the disjointness of regions r and s with a minimal gap
     * @param r first region
     * @param s second region
     * @param layer layer for which this constraint is added
     * @param minimal_gap minimal gap between regions, supposed to be 0 if they are adjacent (THIS IS NOT CHECKED!)
     * @param direction direction in which s lies relative to r
     * @throws IloException
     */
    public void addDisjointness(Region r, Region s, int layer, double minimal_gap, String direction) throws IloException {
        IloNumExpr distance = null;
        switch(direction.toLowerCase().trim()){
            case "above":
                distance = sum(prod(-1, $y_coord.get(layer).get(r.index)), $y_coord.get(layer).get(s.index));
                break;
            case "below":
                distance = sum($y_coord.get(layer).get(r.index), prod(-1, $y_coord.get(layer).get(s.index)));
                break;
            case "right":
                distance = sum(prod(-1, $x_coord.get(layer).get(r.index)), $x_coord.get(layer).get(s.index));
                break;
            case "left":
                distance = sum($x_coord.get(layer).get(r.index), prod(-1, $x_coord.get(layer).get(s.index)));
                break;
            default:
                System.out.println(String.format("The given direction %s, interpreted as %s is not a valid option. Please choose 'below', 'right', 'above' or 'left'.", direction, direction.toLowerCase().trim()));
                break;
        }
        if(r.isObstacle)
            if(s.isObstacle)    add(ge(distance, sum(minimal_gap, prod(0.5, sum($widths.get(layer).get(r.index), $widths.get(layer).get(s.index))))));
            else                add(ge(distance, sum(minimal_gap, prod(0.5, sum($widths.get(layer).get(r.index), s.getSize(layer))))));
        else
            if(s.isObstacle)    add(ge(distance, sum(minimal_gap, prod(0.5, sum(r.getSize(layer), $widths.get(layer).get(s.index))))));
            else                add(ge(distance,      minimal_gap +              0.5 *        (r.getSize(layer) + s.getSize(layer))));
    }

    /**
     * Adds a hard constraint to the program which ensures the additional separation in the second dimension
     * @param r first region
     * @param s second region
     * @param layer layer for which this constraint is added
     * @param direction direction in which the second separation is created! NOT the main separation between the regions
     * @throws IloException
     */
    public void addDoubleDisjointness(Region r, Region s, int layer, String direction) throws IloException {
        //System.out.println(r.code + " is " + direction + " of " + s.code + "");
        IloNumExpr distance = null;
        switch(direction.toLowerCase().trim()){
            case "above":
                distance = sum(prod(-1, $y_coord.get(layer).get(r.index)), $y_coord.get(layer).get(s.index));
                break;
            case "below":
                distance = sum($y_coord.get(layer).get(r.index), prod(-1, $y_coord.get(layer).get(s.index)));
                break;
            case "right":
                distance = sum(prod(-1, $x_coord.get(layer).get(r.index)), $x_coord.get(layer).get(s.index));
                break;
            case "left":
                distance = sum($x_coord.get(layer).get(r.index), prod(-1, $x_coord.get(layer).get(s.index)));
                break;
            default:
                System.out.println(String.format("The given direction %s, interpreted as %s is not a valid option. Please choose 'below', 'right', 'above' or 'left'.", direction, direction.toLowerCase().trim()));
                break;
        }



//        IloNumExpr distance_above = sum(prod(-1, $y_coord.get(layer).get(r.index)), $y_coord.get(layer).get(s.index));
//        IloNumExpr distance_below = sum($y_coord.get(layer).get(r.index), prod(-1, $y_coord.get(layer).get(s.index)));
//        IloNumExpr distance_right = sum(prod(-1, $x_coord.get(layer).get(r.index)), $x_coord.get(layer).get(s.index));
//        IloNumExpr distance_left = sum($x_coord.get(layer).get(r.index), prod(-1, $x_coord.get(layer).get(s.index)));

        if(r.isObstacle)
            if(s.isObstacle)    add(ge(distance, prod(0.5, sum($widths.get(layer).get(r.index), $widths.get(layer).get(s.index)))));
            else                add(ge(distance, prod(0.5, sum($widths.get(layer).get(r.index), s.getSize(layer)))));
        else
            if(s.isObstacle)    add(ge(distance, prod(0.5, sum(r.getSize(layer), $widths.get(layer).get(s.index)))));
            else                add(ge(distance,      0.5 *   (r.getSize(layer) + s.getSize(layer))));
    }

    /**
     * Adds a soft constraint to the program which tries to minimize the distance between regions r and s
     * The regions r and s are supposed to be adjacent. This is not checked by this method.
     * @param r first region
     * @param s second region
     * @param layer layer for which this constraint is added
     * @param direction direction in which s lies relative to r
     * @throws IloException
     */
    //TODO: Scaling for number, scaling for range
    public void addDistanceMinimization(Region r, Region s, int layer, String direction) throws IloException {
        double min_contact = Math.min(r.getSize(layer), s.getSize(layer)) *  Settings.LP.min_contact_factor;
        int first_index, second_index;
        if(r.index<s.index){
            first_index = r.index;
            second_index = s.index;
        } else {
            first_index = s.index;
            second_index = r.index;
        }

        // If we are using the L_inf Metric, we will exclusively use the first of these variables.
        IloNumVar adj_minimizer_x = $adjacency_minimizers.get(layer).get(first_index).get(second_index)[0];
        IloNumVar adj_minimizer_y = Settings.LP.L_infinity ? adj_minimizer_x : $adjacency_minimizers.get(layer).get(first_index).get(second_index)[1];

//        IloNumExpr distance_below = sum(prod(-1, $y_coord.get(layer).get(r.index)), $y_coord.get(layer).get(s.index));
//        IloNumExpr distance_above = sum($y_coord.get(layer).get(r.index), prod(-1, $y_coord.get(layer).get(s.index)));
//        IloNumExpr distance_left = sum(prod(-1, $x_coord.get(layer).get(r.index)), $x_coord.get(layer).get(s.index));
//        IloNumExpr distance_right = sum($x_coord.get(layer).get(r.index), prod(-1, $x_coord.get(layer).get(s.index)));


        IloNumExpr distance_above = sum(prod(-1, $y_coord.get(layer).get(r.index)), $y_coord.get(layer).get(s.index));
        IloNumExpr distance_below = sum($y_coord.get(layer).get(r.index), prod(-1, $y_coord.get(layer).get(s.index)));
        IloNumExpr distance_right = sum(prod(-1, $x_coord.get(layer).get(r.index)), $x_coord.get(layer).get(s.index));
        IloNumExpr distance_left = sum($x_coord.get(layer).get(r.index), prod(-1, $x_coord.get(layer).get(s.index)));

        IloNumExpr distance_main = null;
        IloNumExpr distance_secondary_1 = null;
        IloNumExpr distance_secondary_2 = null;
        IloNumVar main_minimizer = null;
        IloNumVar secondary_minimizer = null;
        switch(direction.toLowerCase().trim()){
            case "below":
                distance_main        = distance_below;
                distance_secondary_1 = distance_left;
                distance_secondary_2 = distance_right;
                main_minimizer = adj_minimizer_y;
                secondary_minimizer = adj_minimizer_x;
                break;
            case "above":
                distance_main        = distance_above;
                distance_secondary_1 = distance_left;
                distance_secondary_2 = distance_right;
                main_minimizer = adj_minimizer_y;
                secondary_minimizer = adj_minimizer_x;
                break;
            case "left":
                distance_main        = distance_left;
                distance_secondary_1 = distance_below;
                distance_secondary_2 = distance_above;
                main_minimizer = adj_minimizer_x;
                secondary_minimizer = adj_minimizer_y;
                break;
            case "right":
                distance_main        = distance_right;
                distance_secondary_1 = distance_below;
                distance_secondary_2 = distance_above;
                main_minimizer = adj_minimizer_x;
                secondary_minimizer = adj_minimizer_y;
                break;
            default:
                System.out.println(String.format("The given direction %s, interpreted as %s is not a valid option. Please choose 'below', 'right', 'above' or 'left'.", direction, direction.toLowerCase().trim()));
                break;
        }

        /*
        double r_obst_switch = r.isObstacle ? Settings.LP.obstacle_upper_bound : 1;
        double s_obst_switch = s.isObstacle ? Settings.LP.obstacle_upper_bound : 1;
        add(ge(main_minimizer, sum(distance_main, -0.5 * (r.getSize(layer)*r_obst_switch + s.getSize(layer)*s_obst_switch))));
        add(ge(secondary_minimizer, sum(distance_secondary_1, -0.5 * (r.getSize(layer)*r_obst_switch + s.getSize(layer)*s_obst_switch) + min_contact)));
        add(ge(secondary_minimizer, sum(distance_secondary_2, -0.5 * (r.getSize(layer)*r_obst_switch + s.getSize(layer)*s_obst_switch) + min_contact)));
        */


        if(r.isObstacle)
            if(s.isObstacle){
                add(ge(main_minimizer, sum(distance_main, prod(-0.5, sum($widths.get(layer).get(r.index), $widths.get(layer).get(s.index))))));
                add(ge(secondary_minimizer, sum(distance_secondary_1, sum(prod(-0.5, sum($widths.get(layer).get(r.index), $widths.get(layer).get(s.index))), min_contact))));
                add(ge(secondary_minimizer, sum(distance_secondary_2, sum(prod(-0.5, sum($widths.get(layer).get(r.index), $widths.get(layer).get(s.index))), min_contact))));
            } else {
                add(ge(main_minimizer, sum(distance_main, prod(-0.5, sum($widths.get(layer).get(r.index), s.getSize(layer))))));
                add(ge(secondary_minimizer, sum(distance_secondary_1, sum(prod(-0.5, sum($widths.get(layer).get(r.index), s.getSize(layer))), min_contact))));
                add(ge(secondary_minimizer, sum(distance_secondary_2, sum(prod(-0.5, sum($widths.get(layer).get(r.index), s.getSize(layer))), min_contact))));
            }
        else
            if(s.isObstacle) {
                add(ge(main_minimizer, sum(distance_main, prod(-0.5, sum(r.getSize(layer), $widths.get(layer).get(s.index))))));
                add(ge(secondary_minimizer, sum(distance_secondary_1, sum(prod(-0.5, sum(r.getSize(layer), $widths.get(layer).get(s.index))), min_contact))));
                add(ge(secondary_minimizer, sum(distance_secondary_2, sum(prod(-0.5, sum(r.getSize(layer), $widths.get(layer).get(s.index))), min_contact))));
            } else {
                add(ge(main_minimizer, sum(distance_main, -0.5 * (r.getSize(layer) + s.getSize(layer)))));
                add(ge(secondary_minimizer, sum(distance_secondary_1, -0.5 * (r.getSize(layer) + s.getSize(layer)) + min_contact)));
                add(ge(secondary_minimizer, sum(distance_secondary_2, -0.5 * (r.getSize(layer) + s.getSize(layer)) + min_contact)));
            }

        double obstacle_factor = (r.isObstacle || s.isObstacle) ? 0.1 : 1;

        if(Settings.LP.L_infinity) {
            objective = sum(objective, prod(distance_weight * obstacle_factor, adj_minimizer_x));
        } else {
            objective = sum(objective, prod((distance_weight * obstacle_factor)/2, adj_minimizer_x));
            objective = sum(objective, prod((distance_weight * obstacle_factor)/2, adj_minimizer_y));
        }
    }

    /**
     * Adds a soft constraint to the program which tries to keep the adjacency between regions r and s
     * The regions r and s are supposed to be adjacent. This is not checked by this method.
     * THIS CONSTRAINT TURNS THIS INTO AN MIP!
     * @param r
     * @param s
     * @param layer
     * @param direction
     */
    //TODO: Scaling ?
    public void addTopologyMinimization(Region r, Region s, int layer, String direction) throws IloException {
//        if(r.code.equals("NY") && s.code.equals("PA")) {
//            System.out.println(r.code + "::" + s.code);
//            System.out.println(direction);
//        }
//        if(r.code.equals("PA") && s.code.equals("NY")) {
//            System.out.println(r.code + "::" + s.code);
//            System.out.println(direction);
//        }
        double min_contact = Math.min(r.getSize(layer), s.getSize(layer)) * Settings.LP.min_contact_factor;
//        System.out.println(min_contact);
        int first_index, second_index;
        if(r.index<s.index){
            first_index = r.index;
            second_index = s.index;
        } else {
            first_index = s.index;
            second_index = r.index;
        }

        IloNumExpr distance_above = sum(prod(-1, $y_coord.get(layer).get(r.index)), $y_coord.get(layer).get(s.index));
        IloNumExpr distance_below = sum($y_coord.get(layer).get(r.index), prod(-1, $y_coord.get(layer).get(s.index)));
        IloNumExpr distance_right = sum(prod(-1, $x_coord.get(layer).get(r.index)), $x_coord.get(layer).get(s.index));
        IloNumExpr distance_left = sum($x_coord.get(layer).get(r.index), prod(-1, $x_coord.get(layer).get(s.index)));

        IloNumExpr distance_main = null;
        IloNumExpr distance_main2 = null;
        IloNumExpr distance_secondary_1 = null;
        IloNumExpr distance_secondary_2 = null;


        switch(direction.toLowerCase().trim()){
            case "below":
                distance_main        = distance_below;
                distance_main2       = distance_above;
                distance_secondary_1 = distance_left;
                distance_secondary_2 = distance_right;
                break;
            case "above":
                distance_main        = distance_above;
                distance_main2       = distance_below;
                distance_secondary_1 = distance_left;
                distance_secondary_2 = distance_right;
                break;
            case "left":
                distance_main        = distance_left;
                distance_main2       = distance_right;
                distance_secondary_1 = distance_below;
                distance_secondary_2 = distance_above;
                break;
            case "right":
                distance_main        = distance_right;
                distance_main2       = distance_left;
                distance_secondary_1 = distance_below;
                distance_secondary_2 = distance_above;
                break;
            default:
                System.out.println(String.format("The given direction %s, interpreted as %s is not a valid option. Please choose 'below', 'right', 'above' or 'left'.", direction, direction.toLowerCase().trim()));
                break;
        }

        IloNumVar adj_counter = $adjacency_counters.get(layer).get(first_index).get(second_index);
        /*double d_x = r.getOriginalLocation().getX() - s.getOriginalLocation().getX();
        double d_y = r.getOriginalLocation().getY() - s.getOriginalLocation().getY();

        if (d_x > 0) {
            distance_h = sum($x_coord.get(layer).get(r.index), prod(-1, $x_coord.get(layer).get(s.index)));
        } else {
            distance_h = sum(prod(-1, $x_coord.get(layer).get(r.index)), $x_coord.get(layer).get(s.index));
        }

        if(d_y > 0){
            distance_v = sum($y_coord.get(layer).get(r.index), prod(-1, $y_coord.get(layer).get(s.index)));
        } else {
            distance_v = sum(prod(-1, $y_coord.get(layer).get(r.index)), $y_coord.get(layer).get(s.index));
        }

        if (d_x > d_y) {
            min_contact_h = 0;
            min_contact_v = min_contact;
        } else {
            min_contact_h = min_contact;
            min_contact_v = 0;
        }
        switch(direction.toLowerCase().strip()){
            case "top":
            case "bottom":
                min_contact_h = min_contact;
                min_contact_v = 0;
                break;
            case "left":
            case "right":
                break;
            default:
                System.out.println(String.format("The given direction %s, interpreted as %s is not a valid option. Please choose 'top', 'right', 'bottom' or 'left'.", direction, direction.toLowerCase().strip()));
                break;
        }*/

        if(r.isObstacle) {
            if (s.isObstacle) {
                if(use_big_m){
                    add(le(sum(sum(distance_main, prod(-0.5, sum($widths.get(layer).get(r.index), $widths.get(layer).get(s.index)))), prod(-Settings.LP.big_constant, adj_counter)), DoubleUtil.EPS));
                    add(le(sum(sum(distance_secondary_1, sum(min_contact, prod(-0.5, sum($widths.get(layer).get(r.index), $widths.get(layer).get(s.index))))), prod(-Settings.LP.big_constant, adj_counter)), DoubleUtil.EPS));
                    add(le(sum(sum(distance_secondary_2, sum(min_contact, prod(-0.5, sum($widths.get(layer).get(r.index), $widths.get(layer).get(s.index))))), prod(-Settings.LP.big_constant, adj_counter)), DoubleUtil.EPS));
                } else {
                    //add(ifThen(eq(adj_counter, 0), le(distance_main2, prod(-0.5, sum($widths.get(layer).get(r.index), $widths.get(layer).get(s.index))))));
                    add(ifThen(eq(adj_counter, 0), le(distance_main, prod(-0.5, sum($widths.get(layer).get(r.index), $widths.get(layer).get(s.index))))));
                    add(ifThen(eq(adj_counter, 0), le(distance_secondary_1, sum(min_contact, prod(-0.5, sum($widths.get(layer).get(r.index), $widths.get(layer).get(s.index)))))));
                    add(ifThen(eq(adj_counter, 0), le(distance_secondary_2, sum(min_contact, prod(-0.5, sum($widths.get(layer).get(r.index), $widths.get(layer).get(s.index)))))));
                }
            } else {
                if(use_big_m){
                    add(le(sum(sum(distance_main, prod(-0.5, sum($widths.get(layer).get(r.index), s.getSize(layer)))), prod(-Settings.LP.big_constant, adj_counter)), 0));
                    add(le(sum(sum(distance_secondary_1, sum(min_contact, prod(-0.5, sum($widths.get(layer).get(r.index), s.getSize(layer))))), prod(-Settings.LP.big_constant, adj_counter)), 0));
                    add(le(sum(sum(distance_secondary_2, sum(min_contact, prod(-0.5, sum($widths.get(layer).get(r.index), s.getSize(layer))))), prod(-Settings.LP.big_constant, adj_counter)), 0));
                } else {
                    //add(ifThen(eq(adj_counter, 0), le(distance_main2, prod(-0.5, sum($widths.get(layer).get(r.index), s.getSize(layer))))));
                    add(ifThen(eq(adj_counter, 0), le(distance_main, prod(-0.5, sum($widths.get(layer).get(r.index), s.getSize(layer))))));
                    add(ifThen(eq(adj_counter, 0), le(distance_secondary_1, sum(min_contact, prod(-0.5, sum($widths.get(layer).get(r.index), s.getSize(layer)))))));
                    add(ifThen(eq(adj_counter, 0), le(distance_secondary_2, sum(min_contact, prod(-0.5, sum($widths.get(layer).get(r.index), s.getSize(layer)))))));
                }
            }
        } else {
            if (s.isObstacle) {
                if(use_big_m){
                    add(le(sum(sum(distance_main, prod(-0.5, sum(r.getSize(layer), $widths.get(layer).get(s.index)))), prod(-Settings.LP.big_constant, adj_counter)), 0));
                    add(le(sum(sum(distance_secondary_1, sum(min_contact, prod(-0.5, sum(r.getSize(layer), $widths.get(layer).get(s.index))))), prod(-Settings.LP.big_constant, adj_counter)), 0));
                    add(le(sum(sum(distance_secondary_2, sum(min_contact, prod(-0.5, sum(r.getSize(layer), $widths.get(layer).get(s.index))))), prod(-Settings.LP.big_constant, adj_counter)), 0));
                } else {
                    //add(ifThen(eq(adj_counter, 0), le(distance_main2, prod(-0.5, sum(r.getSize(layer), $widths.get(layer).get(s.index))))));
                    add(ifThen(eq(adj_counter, 0), le(distance_main, prod(-0.5, sum(r.getSize(layer), $widths.get(layer).get(s.index))))));
                    add(ifThen(eq(adj_counter, 0), le(distance_secondary_1, sum(min_contact, prod(-0.5, sum(r.getSize(layer), $widths.get(layer).get(s.index)))))));
                    add(ifThen(eq(adj_counter, 0), le(distance_secondary_2, sum(min_contact, prod(-0.5, sum(r.getSize(layer), $widths.get(layer).get(s.index)))))));
                }

            } else {
                if(use_big_m){
                    add(le(sum(sum(distance_main, (-0.5 * (r.getSize(layer) + s.getSize(layer)))), prod(-Settings.LP.big_constant, adj_counter)), 0));
                    add(le(sum(sum(distance_secondary_1, min_contact + (-0.5 * (r.getSize(layer) + s.getSize(layer)))), prod(-Settings.LP.big_constant, adj_counter)), 0));
                    add(le(sum(sum(distance_secondary_2, min_contact + (-0.5 * (r.getSize(layer) + s.getSize(layer)))), prod(-Settings.LP.big_constant, adj_counter)), 0));
//                    add(le(sum(distance_secondary_1, min_contact + (-0.5 * (r.getSize(layer) + s.getSize(layer)))), prod(Settings.LP.big_constant, adj_counter)));
//                    add(le(sum(distance_secondary_2, min_contact + (-0.5 * (r.getSize(layer) + s.getSize(layer)))), prod(Settings.LP.big_constant, adj_counter)));
                } else {
//                    add(ifThen(eq(adj_counter, 0), le(distance_main2,0.5*(r.getSize(layer)+s.getSize(layer)))));
                    add(ifThen(eq(adj_counter, 0), le(distance_main,0.5*(r.getSize(layer)+s.getSize(layer)))));
                    add(ifThen(eq(adj_counter, 0), le(distance_secondary_1, -min_contact + 0.5 * (r.getSize(layer) + s.getSize(layer)))));
                    add(ifThen(eq(adj_counter, 0), le(distance_secondary_2, -min_contact + 0.5 * (r.getSize(layer) + s.getSize(layer)))));
                }
            }
        }

        double obstacle_factor = (r.isObstacle || s.isObstacle) ? 0.1 : 1;
        objective = sum(objective, prod(topology_weight * obstacle_factor, adj_counter));
    }

    /**
     * Adds a soft constraint to the program which tries to keep the angle between regions r and s
     * @param r first region
     * @param s second region
     * @param layer layer for which this constraint is added
     * @param multiplier
     */
    //TODO: Scaling for number, scaling for range
    public void addAngleNuance(Region r, Region s, int layer, String direction, double multiplier) throws IloException {

//        double delta_x = Math.abs(r.getOriginalLocation().getX() - s.getOriginalLocation().getX());
//        double delta_y = Math.abs(r.getOriginalLocation().getY() - s.getOriginalLocation().getY());

        if (!r.isObstacle) {
            int first_index, second_index;
            if (r.index < s.index) {
                first_index = r.index;
                second_index = s.index;
            } else {
                first_index = s.index;
                second_index = r.index;
            }
            IloNumVar nuance_minimizer = $nuance_minimizers.get(layer).get(first_index).get(second_index);

            double inslope = 0;


//            if(Math.abs(delta_x) > Math.abs(delta_y)) {
//                if(delta_x > 0) {
//                    direction = "right";
//                } else {
//                    direction = "left";
//                }
//            } else {
//                if (delta_y > 0) {
//                    direction = "above";
//                } else {
//                    direction = "below";
//                }
//            }


            if(direction.equals("left")||direction.equals("right")) {
                inslope = (r.getOriginalLocation().getX() - s.getOriginalLocation().getX()) / (r.getOriginalLocation().getY() - s.getOriginalLocation().getY());
                add(le(sum(
                        $y_coord.get(layer).get(s.index),
                        prod(-1, $y_coord.get(layer).get(r.index)),
                        prod( 1.0/inslope, $x_coord.get(layer).get(r.index)),
                        prod(-1.0/inslope, $x_coord.get(layer).get(s.index))
                ), nuance_minimizer));
                add(le(sum(
                        $y_coord.get(layer).get(r.index),
                        prod(-1, $y_coord.get(layer).get(s.index)),
                        prod( 1.0/inslope, $x_coord.get(layer).get(s.index)),
                        prod(-1.0/inslope, $x_coord.get(layer).get(r.index))
                ), nuance_minimizer));
            } else {
                inslope = (r.getOriginalLocation().getY() - s.getOriginalLocation().getY()) / (r.getOriginalLocation().getX() - s.getOriginalLocation().getX());
                add(le(sum(
                        $x_coord.get(layer).get(s.index),
                        prod(-1, $x_coord.get(layer).get(r.index)),
                        prod( 1.0/inslope, $y_coord.get(layer).get(r.index)),
                        prod(-1.0/inslope, $y_coord.get(layer).get(s.index))
                ), nuance_minimizer));
                add(le(sum(
                        $x_coord.get(layer).get(r.index),
                        prod(-1, $x_coord.get(layer).get(s.index)),
                        prod( 1.0/inslope, $y_coord.get(layer).get(s.index)),
                        prod(-1.0/inslope, $y_coord.get(layer).get(r.index))
                ), nuance_minimizer));
            }

            objective = sum(objective, prod(nuance_weight, nuance_minimizer));
        }
    }

    /**
     * Adds a soft constraint to the program which tries to minimize the distance between region r and its input position
     * @param r first region
     * @param layer layer for which this constraint is added
     */
    //TODO: Scaling for number, scaling for range
    public void addOrigin(Region r, int layer) throws IloException {
        Vector origin = r.getOriginalLocation();
        double o_x = origin.getX(), o_y = origin.getY();

        IloNumVar origin_minimizer_x = $origin_minimizers.get(layer).get(r.index)[0];
        IloNumVar origin_minimizer_y = Settings.LP.L_infinity ? origin_minimizer_x : $origin_minimizers.get(layer).get(r.index)[1];

        add(le(sum(
                    o_x,
                    prod(-1, $x_coord.get(layer).get(r.index))
        ),origin_minimizer_x));
        add(le(sum(
                -o_x,
                $x_coord.get(layer).get(r.index)
        ),origin_minimizer_x));
        add(le(sum(
                o_y,
                prod(-1, $y_coord.get(layer).get(r.index))
        ),origin_minimizer_y));
        add(le(sum(
                -o_y,
                $y_coord.get(layer).get(r.index)
        ),origin_minimizer_y));

        if(Settings.LP.L_infinity) {
            objective = sum(objective, prod(origin_weight, origin_minimizer_x));
        } else {
            objective = sum(objective, prod(origin_weight/2, origin_minimizer_x));
            objective = sum(objective, prod(origin_weight/2, origin_minimizer_y));
        }
    }

    /**
     * Adds a soft constraint to the program which tries to minimize the distance between region r and itself in a different layer
     * @param r first region
     * @param layer_a first layer
     * @param layer_b second layer
     * @param stable_reverse
     */
    //TODO: Scaling for number, scaling for range
    public void addStability(Region r, int layer_a, int layer_b, boolean stable_reverse) throws IloException {

//        System.out.println($stability_minimizers.keySet());

//        HashMap<Integer, HashMap<Integer, IloNumVar[]>> A = $stability_minimizers.get(layer_a);
//        System.out.println();
//        System.out.println(A.keySet());
//        HashMap<Integer, IloNumVar[]> B = A.get(layer_b);
//        System.out.println(B.keySet());
//        IloNumVar[] C = B.get(r.index);
//        IloNumVar D = C[0];
//        IloNumVar E = C[1];

        IloNumVar stability_minimizer_x = $stability_minimizers.get(layer_a).get(layer_b).get(r.index)[0];

        IloNumVar stability_minimizer_y = Settings.LP.L_infinity ? stability_minimizer_x : $stability_minimizers.get(layer_a).get(layer_b).get(r.index)[1];

//        System.out.println(layers);
        if(stable_reverse){

            add(le(sum(
                    $x_coord.get(layer_a).get(r.index),
                    prod(-1, $x_coord.get(layer_b).get(r.index))
            ),stability_minimizer_x));
            add(le(sum(
                    $x_coord.get(layer_b).get(r.index),
                    prod(-1, $x_coord.get(layer_a).get(r.index))
            ),stability_minimizer_x));
            add(le(sum(
                    $y_coord.get(layer_a).get(r.index),
                    prod(-1, $y_coord.get(layer_b).get(r.index))
            ),stability_minimizer_y));
            add(le(sum(
                    $y_coord.get(layer_b).get(r.index),
                    prod(-1, $y_coord.get(layer_a).get(r.index))
            ),stability_minimizer_y));

        } else {
//            System.out.println(layer_a + " - " + layer_b);
            add(le(sum(
                    $x_coord.get(layer_a).get(r.index),
                    -r.getPosition(layer_b).getX()
            ),stability_minimizer_x));
            add(le(sum(
                    r.getPosition(layer_b).getX(),
                    prod(-1, $x_coord.get(layer_a).get(r.index))
            ),stability_minimizer_x));
            add(le(sum(
                    $y_coord.get(layer_a).get(r.index),
                    -r.getPosition(layer_b).getY()
            ),stability_minimizer_y));
            add(le(sum(
                    r.getPosition(layer_b).getY(),
                    prod(-1, $y_coord.get(layer_a).get(r.index))
            ),stability_minimizer_y));
        }

        if(Settings.LP.L_infinity) {
            objective = sum(objective, prod(stability_weight, stability_minimizer_x));
        } else {
            objective = sum(objective, prod(stability_weight/2, stability_minimizer_x));
            objective = sum(objective, prod(stability_weight/2, stability_minimizer_y));
        }
//        System.out.println("position of " + r.code + " in layer " + layer_b + " during constraint adding: " + r.getPosition(layer_b));
    }

    public void addDummyBool() throws IloException {
        add(ge(boolVar(), 1));
    }

    public void changeMIPStartPositionsPerLayer(CartogramModel c, Integer layer, HashMap<Integer, Vector> positions) throws IloException {
        HashMap<Integer, IloNumVar> $x_coord_layer = $x_coord.get(layer);
        HashMap<Integer, IloNumVar> $y_coord_layer = $y_coord.get(layer);
        IloNumVar[] x_coords_array = new IloNumVar[c.getRegionCount(layer)];
        double[] x_values_array = new double[c.getRegionCount(layer)];
        IloNumVar[] y_coords_array = new IloNumVar[c.getRegionCount(layer)];
        double[] y_values_array = new double[c.getRegionCount(layer)];
        int id = 0;
        for (Region region : c.getRegions(layer)) {
            Vector initial_pos = positions.get(region.index);
            x_coords_array[id] = $x_coord_layer.get(region.index);
            y_coords_array[id] = $y_coord_layer.get(region.index);
            x_values_array[id] = initial_pos.getX();
            y_values_array[id] = initial_pos.getY();
            id++;
        }
        changeMIPStart(0, x_coords_array, x_values_array);
        changeMIPStart(0, y_coords_array, y_values_array);
        System.out.println("Set warm start positions for layer "+ layer);
    }

    public void changeMIPStartContactsPerLayer(CartogramModel c, Integer layer, HashMap<Integer, Vector> integerVectorHashMap) throws IloException {
        HashMap<Integer, HashMap<Integer, IloNumVar>> layer_counters = $adjacency_counters.get(layer);

        int num_counters = 0;
        for (Integer firstnumber : layer_counters.keySet()) {
            num_counters += layer_counters.get(firstnumber).size();
        }

        IloNumVar[] contacts_array = new IloIntVar[num_counters];
        double[] counter_values = new double[num_counters];
        int id = 0;
        List<Region> regions = c.getRegions(layer);
        for (int i = 0; i < regions.size(); i++) {
            Region region1 = regions.get(i);
            for (int i1 = i+1; i1 < regions.size(); i1++) {
                Region region2 = regions.get(i1);
                if(c.adjacent(region1, region2, layer)) {
                    contacts_array[id] = layer_counters.get(region1.index).get(region2.index);
                    counter_values[id] = adjKept(region1, region2, layer) ? 1 : 0;
                    id++;
                }
            }
        }
        changeMIPStart(0, contacts_array, counter_values);
        System.out.println("Set warm start contact information for layer "+ layer);
    }

    // DUPLICATE FROM EVALUATION
    private static boolean adjKept(Region r, Region s, int layer) {
        double x_dist = Math.abs(r.getPosition(layer).getX() - s.getPosition(layer).getX());
        double y_dist = Math.abs(r.getPosition(layer).getY() - s.getPosition(layer).getY());
        double dist_is = Math.max(x_dist, y_dist);
        double dist_should = (r.getSize(layer) + s.getSize(layer))/2.0;
        if (dist_is < dist_should + DoubleUtil.EPS){
            return true;
        } else {
            return false;
        }
    }

    public void printBinaries(CartogramModel c) {
        for (Integer layer : layers) {
            for (Region region1 : c.getRegions(layer)) {
                for (Region region2 : c.getRegions(layer)) {

                    if(region1.index >= region2.index || !c.adjacent(region1, region2, layer)) continue;
                    int i1 = region1.index;
                    int i2 = region2.index;

//                    try {
//                        System.out.println(region1.code + "-" + region2.code + ": " + getValue($adjacency_counters.get(layer).get(i1).get(i2)) + " of type " + $adjacency_counters.get(layer).get(i1).get(i2).getType());
//
//                    } catch (IloException e) {
//                        e.printStackTrace();
//                    }
                }
            }
        }
    }
}
