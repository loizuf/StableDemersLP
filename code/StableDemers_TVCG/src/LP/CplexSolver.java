package LP;

import Main.RunResults;
import Main.Settings;
import Main.Solver;
import Model.CartogramModel;
import Model.Region;
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import nl.tue.geometrycore.geometry.Vector;

import java.io.IOException;
import java.util.*;

public class CplexSolver extends Solver {

    // cplex variables
    // one PER COMPONENT!
    protected List<CartogramCplex> programs;
    private CartogramModel c;
    List<Set<Integer>> iterative_order;
    private double start;


    public double getStart() {
        return start;
    }

    @Override
    public RunResults run(CartogramModel c, List<Set<Integer>> iterative_order) throws IloException {
        this.c = c;

        /*
         * The iterative order contains the sets of layers which need to be computed together, every set does not have stability to succeding layers
         * This is also relevant if we do not use the iterative optimization since it is possible to have a disconnected stability model
         */
        this.iterative_order = iterative_order;
        RunResults results_prelim = new RunResults();


        if(Settings.LP.mip_warmstart && (Settings.LP.disjoint_ilp_c || Settings.LP.topology_c)) {
            System.out.println("Starting Warmstart");
            boolean separation_decision = false;
            boolean adjacency_decision = false;

            if(Settings.LP.disjoint_ilp_c) {
                separation_decision = true;
                Settings.LP.disjoint_ilp_c = false;
                Settings.LP.disjoint_c = true;
            }

            if(Settings.LP.topology_c) {
                adjacency_decision = true;
                Settings.LP.topology_c = false;
                Settings.LP.distance_minimization_c = true;
            }

            buildModel(c);

            for (int i = 0; i < programs.size(); i++) {
                results_prelim = run(i, results_prelim);
                results_prelim.addLostAdjacencies(calculate_lost_adjacencies(programs.get(i)));
                results_prelim.addPositions(retrieve_calculated_region_positions(programs.get(i)));
                results_prelim.addSizes(retrieve_calculated_obstacle_sizes(programs.get(i)));

//                try {
//                    c.printComprehensiveReport();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
            }

            if(separation_decision) {
                Settings.LP.disjoint_ilp_c = true;
                // Assumption that these two constraints are not used simultaneously
                Settings.LP.disjoint_c = false;
            }

            if(adjacency_decision) {
                Settings.LP.topology_c = true;
                // Assumption that these two constraints are not used simultaneously
                Settings.LP.distance_minimization_c = false;
            }
        }


        double M = 0;
        for (int i = 0; i < c.getLayerCount(); i++) {
            double layerM = 0;
            for (Region region : c.getRegions()) {
                layerM += region.getSize(i);
            }
            if (layerM > M) {
                M = layerM;
            }
        }
        System.out.println("Big constant set to " + M);
        Settings.LP.big_constant = M;
//        System.out.println(100*M);
//        Settings.LP.big_constant = 160000;

        buildModel(c);

        if(Settings.LP.mip_warmstart && (Settings.LP.disjoint_ilp_c || Settings.LP.topology_c)) {
            setInitialValues(c, results_prelim);
        }

        RunResults results = new RunResults();

        for (int i = 0; i < programs.size(); i++) {
            CartogramCplex program = programs.get(i);

            if(Settings.LP.mip_warmstart && (Settings.LP.disjoint_ilp_c || Settings.LP.topology_c)) {
                //System.out.println("Warm Start: " + programs.get(i).getMIPStartName(1));
            }
            results = run(i, results);
            results.addLostAdjacencies(calculate_lost_adjacencies(programs.get(i)));
            HashMap<Integer, HashMap<Integer, Vector>> uppos = retrieve_calculated_region_positions(programs.get(i));
            results.addPositions(uppos);
            results.addSizes(retrieve_calculated_obstacle_sizes(programs.get(i)));

            for (Map.Entry<Integer, HashMap<Integer, Vector>> entry : uppos.entrySet()) {
                System.out.println("updating");
                c.updateRegionPositions(entry.getKey(), entry.getValue());
            }

            buildModel(c);

//            if(Settings.LP.topology_c) {
//                for (Integer layer : program.getLayers()) {
//                    ArrayList<IloNumVar> vars = program.getAdjacencyCounters(layer);
//                    for (IloNumVar var : vars) {
//                        System.out.println(var.getName() + " is " + (program.getValue(var) > 0 ? "NO" : "kept"));
////                            lost_adjacencies.put(layer, counter);
//                    }
//                }
//            }
//            try {
//                c.printComprehensiveReport();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
        }
        return results;
    }

    private void setInitialValues(CartogramModel c, RunResults results_prelim) throws IloException {
        for (CartogramCplex program : programs) {
            for (Integer layer : program.getLayers()) {
                program.changeMIPStartPositionsPerLayer(c, layer, results_prelim.getPositions().get(layer));
                program.changeMIPStartContactsPerLayer(c, layer, results_prelim.getPositions().get(layer));
            }
        }
    }

    /**
     * This builds the LP Model for a given Cartogram instance.
     * @param c this is the model of the cartogram instance
     *
     */
    private void buildModel(CartogramModel c) throws IloException {
        //iterative_order = Tarjan.getStrongConnectedComponents(c.getStabilities());

        /*
         * Clear and initialize the set of programs
         */
        programs = new ArrayList<>();

        /* This sets up the cplex programs including all variables which are setup and added to the singular cplex programs */
        setup(c);
        System.out.println("LP Setup done");

        /* This adds all constraints to the programs according to the Settings.LP set in the Settings.LP object */
        addConstraints(c);
        System.out.println("LP constraints added");

        for (int program_index = 0; program_index < programs.size(); program_index++) {
            CartogramCplex program = programs.get(program_index);
            /* The next line writes the creates model to a file */
            if (Settings.LP.export_LP_model){
                program.exportModel(Settings.out_path + program_index + "_build" + Settings.export_extension);
            }
        }
    }

    private void setup(CartogramModel c) throws IloException {

        for (int comp = 0; comp < iterative_order.size(); comp++) {
            Set<Integer> layers_in_program = iterative_order.get(comp);
            int total_region_count_in_program = 0;
            int total_squared_region_count_in_program = 0;
            int total_adjacent_region_count_in_program = 0;
            int total_stability_layer_count_in_program = 0;
            for (Integer layer_number : layers_in_program) {
                total_region_count_in_program += c.getRegions(layer_number).size();
                total_squared_region_count_in_program += (c.getRegions(layer_number).size() * (c.getRegions(layer_number).size()-1))/2;
                total_adjacent_region_count_in_program += c.getNumberOfAdjacentRegions(layer_number);
//                total_stability_layer_count_in_program += c.getNumberOfStableRegions(layer_number, layers_in_program);
                total_stability_layer_count_in_program += c.getNumberOfStableRegions(layer_number);
            }
            CartogramCplex ccplex = new CartogramCplex(layers_in_program);
            ccplex.setupObjectiveFunctionWeights(
                    total_region_count_in_program,
                    total_squared_region_count_in_program,
                    total_adjacent_region_count_in_program,
                    total_stability_layer_count_in_program,
                    c.getInputBoundingBox().width() + c.getInputBoundingBox().height(),
                    layers_in_program.size(),
                    c.getMaxSize());
            ccplex.setParam(IloCplex.IntParam.Threads, Settings.LP.THREADS);

            HashMap<Integer, HashMap<Integer, IloNumVar>> x_coords = new HashMap<>();
            HashMap<Integer, HashMap<Integer, IloNumVar>> y_coords = new HashMap<>();
            HashMap<Integer, HashMap<Integer, IloNumVar>> widths = new HashMap<>();
            HashMap<Integer, HashMap<Integer, HashMap<Integer, IloIntVar[]>>> direction_chooser = new HashMap<>();
            HashMap<Integer, HashMap<Integer, HashMap<Integer, IloNumVar[]>>> adjacency_minimizers = new HashMap<>();
            HashMap<Integer, HashMap<Integer, HashMap<Integer, IloNumVar>>> adjacency_counters = new HashMap<>();
            HashMap<Integer, HashMap<Integer, HashMap<Integer, IloNumVar>>> nuance_minimizers = new HashMap<>();
            HashMap<Integer, HashMap<Integer, IloNumVar[]>> origin_minimizers = new HashMap<>();
            HashMap<Integer, HashMap<Integer, HashMap<Integer, IloNumVar[]>>> stability_minimizers = new HashMap<>();

            for (Integer layer : iterative_order.get(comp)) {
                HashMap<Integer, IloNumVar> layer_x_coords = new HashMap<>();
                HashMap<Integer, IloNumVar> layer_y_coords = new HashMap<>();
                HashMap<Integer, IloNumVar> layer_widths = new HashMap<>();
                HashMap<Integer, HashMap<Integer, IloIntVar[]>> layer_direction_chooser = new HashMap<>();
                HashMap<Integer, HashMap<Integer, IloNumVar[]>> layer_adjacency_minimizers = new HashMap<>();
                HashMap<Integer, HashMap<Integer, IloNumVar>> layer_adjacency_counters = new HashMap<>();
                HashMap<Integer, HashMap<Integer, IloNumVar>> layer_nuance_minimizers = new HashMap<>();
                HashMap<Integer, IloNumVar[]> layer_origin_minimizers = new HashMap<>();
                HashMap<Integer, HashMap<Integer, IloNumVar[]>> layer_stability_minimizers = new HashMap<>();
                for (Integer layer_2 : c.getStableLayers(layer)) {
                    layer_stability_minimizers.put(layer_2, new HashMap<>());
                }

                List<Region> layer_regions = c.getRegions(layer);

                for (int i = 0; i < layer_regions.size(); i++) {
                    Region layer_region = layer_regions.get(i);
                    layer_x_coords.put(layer_region.index, ccplex.numVar(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, "x(" + layer_region.name + ")" + layer));
                    layer_y_coords.put(layer_region.index, ccplex.numVar(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, "y(" + layer_region.name + ")" + layer));
                    if (layer_region.isObstacle) {
                        layer_widths.put(layer_region.index,
                                ccplex.numVar(layer_region.getSize(layer) * Settings.LP.obstacle_lower_bound,layer_region.getSize(layer) * Settings.LP.obstacle_upper_bound,"s(" + layer_region.name + ")" + layer
                                ));
                    }
                    for (int j = i + 1; j < layer_regions.size(); j++) {
                        Region layer_region_2 = layer_regions.get(j);

                        int first_index, second_index;
                        if (layer_region.index < layer_region_2.index) {
                            first_index = layer_region.index;
                            second_index = layer_region_2.index;
                        } else {
                            first_index = layer_region_2.index;
                            second_index = layer_region.index;
                        }
                        if(Settings.LP.disjoint_ilp_c) {
                            layer_direction_chooser.computeIfAbsent(first_index, k -> new HashMap<>());
                            layer_direction_chooser.get(first_index).put(second_index, ccplex.boolVarArray(
                                    4,
                                    new String[]{layer_region.name+"_below_"+layer_region_2.name, layer_region.name+"_above_"+layer_region_2.name, layer_region.name+"_left_"+layer_region_2.name, layer_region.name+"_right_"+layer_region_2.name}));
                        }

                        if (Settings.LP.distance_minimization_c && c.adjacent(layer_region, layer_region_2, layer)){

                            // This is old code which made a WRONG assumption
//                            if (Settings.LP.disjoint_ilp_c) {
//                                layer_adjacency_minimizers.computeIfAbsent(first_index, k -> new HashMap<>());
//                                layer_adjacency_minimizers.get(first_index).put(second_index, ccplex.numVarArray(
//                                        2,
//                                        0,
//                                        Double.POSITIVE_INFINITY,
//                                        new String[]{"minX(" + layer_region.name + "," + layer_region_2.name+")"+layer, "minY(" + layer_region.name + "," + layer_region_2.name+")"+layer}));
//                            } else if (c.dominates(layer_region, layer_region_2, layer) || c.dominates(layer_region_2, layer_region, layer)) {
//                                layer_adjacency_minimizers.computeIfAbsent(first_index, k -> new HashMap<>());
//                                layer_adjacency_minimizers.get(first_index).put(second_index, ccplex.numVarArray(
//                                        2,
//                                        0,
//                                        Double.POSITIVE_INFINITY,
//                                        new String[]{"minX(" + layer_region.name + "," + layer_region_2.name+")"+layer, "minY(" + layer_region.name + "," + layer_region_2.name+")"+layer}));
//                            }


                            layer_adjacency_minimizers.computeIfAbsent(first_index, k -> new HashMap<>());
                            layer_adjacency_minimizers.get(first_index).put(second_index, ccplex.numVarArray(
                                    2,
                                    0,
                                    Double.POSITIVE_INFINITY,
                                    new String[]{"minX(" + layer_region.name + "," + layer_region_2.name+")"+layer, "minY(" + layer_region.name + "," + layer_region_2.name+")"+layer}));



                        }
//                        if (Settings.LP.topology_c && c.adjacent(layer_region, layer_region_2, layer) && (c.dominates(layer_region, layer_region_2, layer) || c.dominates(layer_region_2, layer_region, layer))) {
                        if (Settings.LP.topology_c && c.adjacent(layer_region, layer_region_2, layer)) {
                                layer_adjacency_counters.computeIfAbsent(first_index, k -> new HashMap<>());
                            layer_adjacency_counters.get(first_index).put(second_index, ccplex.boolVar("count(" + layer_region.name + "," + layer_region_2.name +")"+layer));
                        }
                        if(Settings.LP.angle_nuance_c) {
                            layer_nuance_minimizers.computeIfAbsent(first_index, k -> new HashMap<>());
                            layer_nuance_minimizers.get(first_index).put(second_index, ccplex.numVar(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, "nu(" + layer_region.name + "," + layer_region_2.name+")"+layer));
                        }
                    }
                    for (Integer layer_2 : c.getStableLayers(layer)) {
                        if(Settings.LP.stability_c && c.getRegions(layer_2).contains(layer_region)) {
                            layer_stability_minimizers.get(layer_2).put(layer_region.index, ccplex.numVarArray(
                                    2,
                                    Double.NEGATIVE_INFINITY,
                                    Double.POSITIVE_INFINITY,
                                    new String[]{"stabX(" + layer_region.name+")"+layer+","+layer_2, "stabY(" + layer_region.name+")"+layer+","+layer_2}));
                        }
                    }
                    if(Settings.LP.origin_displacement_c) {
                        layer_origin_minimizers.put(layer_region.index, ccplex.numVarArray(
                                2,
                                Double.NEGATIVE_INFINITY,
                                Double.POSITIVE_INFINITY,
                                new String[]{"origX(" + layer_region.name+")"+layer, "origY(" + layer_region.name+")"+layer}));
                    }
                }

                x_coords.put(layer, layer_x_coords);
                y_coords.put(layer, layer_y_coords);
                widths.put(layer, layer_widths);
                direction_chooser.put(layer, layer_direction_chooser);
                adjacency_counters.put(layer, layer_adjacency_counters);
                adjacency_minimizers.put(layer, layer_adjacency_minimizers);
                nuance_minimizers.put(layer, layer_nuance_minimizers);
                origin_minimizers.put(layer, layer_origin_minimizers);
                stability_minimizers.put(layer, layer_stability_minimizers);
            }

            ccplex.setXCoords(x_coords);
            ccplex.setYCoords(y_coords);
            ccplex.setWidths(widths);
            ccplex.setDirectionChoosers(direction_chooser);
            ccplex.setAdjacencyCounters(adjacency_counters);
            ccplex.setAdjacencyMinimizers(adjacency_minimizers);
            ccplex.setNuanceMinimizers(nuance_minimizers);
            ccplex.setOriginMinimizers(origin_minimizers);
            ccplex.setStabilityMinimizers(stability_minimizers);
            programs.add(comp, ccplex);
        }
    }

    private void addConstraints(CartogramModel c) throws IloException {
        for (int program_number = 0; program_number < programs.size(); program_number++) {

            Set<Integer> layers_in_program = iterative_order.get(program_number);
            double M = 0;
            for (Integer layer : layers_in_program) {
                double layerM = 0;
                for (Region region : c.getRegions(layer)) {
                    layerM += region.getSize(layer);
                }
                if (layerM > M) {
                    M = layerM;
                }
            }
            System.out.println("Big constant set to " + M/2 + " for program " + program_number);
            Settings.LP.big_constant = M/2;

            int counter1 = 0, counter2 = 0, counter3 = 0, counter4 = 0, counter5 = 0, counter6 = 0, counter7 = 0;
            Set<Integer> layer_set = iterative_order.get(program_number);
            CartogramCplex current_program = programs.get(program_number);
            if (Settings.LP.disjoint_ilp_c || (Settings.LP.topology_c && Settings.LP.mip_warmstart)){
                current_program.addDummyBool();
                current_program.addMIPStart(IloCplex.MIPStartEffort.Auto);
            }
            if(current_program.use_big_m) {
//                System.out.println("USED IN LAYER" + program_number);
                current_program.setParam(IloCplex.Param.MIP.Tolerances.Integrality, 0);
            }
            System.out.println("layers: " + layer_set.size());
            for (Integer layer : layer_set) {
                System.out.println("Adding constraints for layer "+ layer+" in program #"+program_number);
                List<Region> layer_regions = c.getRegions(layer);
                System.out.println("regions in layer " + layer + ": " + layer_regions.size());

                double layer_min, layer_max, layer_average;
                layer_min = c.getMinSize(layer);
                layer_max = c.getMaxSize(layer);

                double minimal_distance = Math.min(layer_min, (layer_max-layer_min));
//                double minimal_distance = 0;

                int count_adjacent = 0;
                for (int first_r_counter = 0; first_r_counter < layer_regions.size(); first_r_counter++) {
                    Region r = layer_regions.get(first_r_counter);

                    for (int second_r_counter = first_r_counter+1; second_r_counter < layer_regions.size(); second_r_counter++) {
                        Region s = layer_regions.get(second_r_counter);

                        double delta_x = s.getOriginalLocation().getX() - r.getOriginalLocation().getX();
                        double delta_y = s.getOriginalLocation().getY() - r.getOriginalLocation().getY();
                        String direction;

                        if(Math.abs(delta_x) > Math.abs(delta_y)) {
                            if(delta_x > 0) {
                                direction = "right";
                            } else {
                                direction = "left";
                            }
                        } else {
                            if (delta_y > 0) {
                                direction = "above";
                            } else {
                                direction = "below";
                            }
                        }


                        boolean adj = c.adjacent(r, s, layer);
                        if(!adj) {
                            if(c.getNeighbours(r, layer).contains(s) || c.getNeighbours(s, layer).contains(r)){
                                System.out.println("SOMETHINGS WRONG");
                            }
                        } else {
                            count_adjacent++;
                        }

                        // This is just one option, other methods are possible
                        double gap = adj ? 0 : minimal_distance;
                        //double gap = adj ? 0 : c.getMinSize();
                        //double gap = adj ? 0 : 5;

                        // Disjointness constraint
                        if (Settings.LP.disjoint_c){
                            //if(!direction.equals("skip"))
                            if (c.dominates(s, r, layer, 'x')) {
                                counter1++;
                                current_program.addDisjointness(r, s, layer, gap, "right");
                            } else if (c.dominates(r, s, layer, 'x')) {
                                counter1++;
                                current_program.addDisjointness(r, s, layer, gap, "left");
                            } else if (c.dominates(s, r, layer, 'y')) {
                                counter1++;
                                current_program.addDisjointness(r, s, layer, gap, "above");
                            } else if (c.dominates(r, s, layer, 'y')) {
                                counter1++;
                                current_program.addDisjointness(r, s, layer, gap, "below");
                            }
                        }

                        // Disjointness constraint
                        if (Settings.LP.disjoint_ilp_c){
                            //if(!direction.equals("skip"))
                            current_program.addILPDisjointness(r, s, layer, gap, direction);
                        }

                        // Double disjointness constraint (Strong setting)
                        if (Settings.LP.double_disjont_c){
                            if (c.secondary_separated(r, s, 'y')) {
                                counter2++;
                                current_program.addDoubleDisjointness(r, s, layer, "below");
                            } else if (c.secondary_separated(s, r, 'y')) {
                                counter2++;
                                current_program.addDoubleDisjointness(r, s, layer, "above");
                            } else if (c.secondary_separated(r, s, 'x')) {
                                counter2++;
                                current_program.addDoubleDisjointness(r, s, layer, "left");
                            } else if (c.secondary_separated(s, r, 'x')) {
                                counter2++;
                                current_program.addDoubleDisjointness(r, s, layer, "right");
                            }
                        }

                        // Main.Main optimization goal
                        if (Settings.LP.distance_minimization_c) {
                            // skip or not?
                            if (adj) {
                                if(Settings.LP.disjoint_ilp_c) {
                                    counter3++;
                                    current_program.addDistanceMinimization(r, s, layer, "below");
                                    current_program.addDistanceMinimization(r, s, layer, "above");
                                    current_program.addDistanceMinimization(r, s, layer, "right");
                                    current_program.addDistanceMinimization(r, s, layer, "left");
                                } else {
                                    counter3++;
                                    current_program.addDistanceMinimization(r, s, layer, direction);
                                }
                            }
                        }

                        // Topology constraint
                        if (Settings.LP.topology_c){
                            if(adj){
                                if(Settings.LP.disjoint_ilp_c) {
                                    counter5++;
                                    current_program.addTopologyMinimization(r, s, layer, "below");
                                    current_program.addTopologyMinimization(r, s, layer, "above");
                                    current_program.addTopologyMinimization(r, s, layer, "right");
                                    current_program.addTopologyMinimization(r, s, layer, "left");
                                } else {
//                                    if (c.dominates(s, r, layer, 'x') || c.dominates(r, s, layer, 'x') || c.dominates(s, r, layer, 'y') || c.dominates(r, s, layer, 'y')) {
//                                        counter5++;
//                                        current_program.addTopologyMinimization(r, s, layer, direction);
//                                    }
                                    counter5++;
                                    current_program.addTopologyMinimization(r, s, layer, direction);
                                }
                            }
                        }

                        // Secondary optimization goal
                        if (Settings.LP.angle_nuance_c) {
                            counter4++;
                            double multiplier =  adj ? 1 : Settings.LP.nuance_non_adjacent_multiplier;
                            current_program.addAngleNuance(r, s, layer, direction, multiplier);
                        }

                    }
                    // Stability constraint
//                    System.out.println(c.getStabilities());
//                    try {
//                        System.in.read();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
                    if (Settings.LP.stability_c){
                        for (int layer_2 = 0; layer_2 < c.getLayerCount(); layer_2++) {
                            if(c.isStableTo(layer, layer_2) && c.getRegions(layer_2).contains(r)) {
//                                System.out.println(layer + " to " + layer_2);
                                counter7++;
                                current_program.addStability(r, layer, layer_2, c.isStableTo(layer_2, layer));
                            }
                        }
//                        for (Integer layer_2 : layer_set) {
//                            if(c.getStabilities().get(layer).contains(layer_2) && c.getRegions(layer_2).contains(r)){
//                                counter7++;
//                                current_program.addStability(r, layer, layer_2);
//                            }
//                        }
                    }

                    // Alternative optimization goal
                    if (Settings.LP.origin_displacement_c){
                        counter6++;
                        current_program.addOrigin(r, layer);
                    }
                }
                System.out.println("layer " + layer + " iterated " + count_adjacent + " adjacent regions");
            }
            current_program.setObjective();
            System.out.println();
            System.out.println("-------------------------");
            System.out.println("Constraint Counts in program " + program_number +
                    ":\ndisjoint: " + counter1 +
                    "\ndouble: " + counter2 +
                    "\ndistance: " + counter3 +
                    "\nnuance: " + counter4 +
                    "\ntopology: " + counter5 +
                    "\norigin: " + counter6 +
                    "\nstability: " + counter7);
            System.out.println("-------------------------");
            System.out.println();
        }
    }

    /**
     * Runs only one of the linear programs. Be careful when using in combination with stability constraints
     * @param program_index index of the program which is been run
     * @return true if the linear program was solved, false if not solution has been found
     */
    private RunResults run(int program_index, RunResults results) throws IloException {
        CartogramCplex program = programs.get(program_index);

//        program.use(new ILPCallback(program, this, c));
        program.setParam(IloCplex.Param.TimeLimit, Settings.LP.time_limit);

        start = program.getCplexTime();
        program.setSolved(program.solve());
        results.addRuntime(program.getCplexTime() - start);
        results.addIterations(program.getNiterations());
        results.addSuccess(program.isSolved());

        if(!program.isSolved()) {
            System.err.println("Model has no solution!");
            if (Settings.LP.export_LP_model){
                program.exportModel(Settings.out_path + program_index + "_failed" + Settings.export_extension);
            }
        } else {
            System.out.println("Solution for model found! OF Value = " + program.getObjValue());
            program.printBinaries(c);
            if (Settings.LP.export_LP_model){
                program.exportModel(Settings.out_path + program_index + "_solved" + Settings.export_extension);
            }
        }
        return results;
    }

    public Map<Integer, Integer> calculate_lost_adjacencies(CartogramCplex program) throws IloException {
        if(!program.isSolved()) System.out.println("This program is not yet solved. Calculating lostadjacencies is not meaningful.");
        Map<Integer, Integer> lost_adjacencies = new HashMap<>();
        if(Settings.LP.distance_minimization_c){
            for (Integer layer : program.getLayers()) {
                int counter = 0;
                ArrayList<IloNumVar[]> vars = program.getAdjacencyMinimizers(layer);
                for (IloNumVar[] var_pair : vars) {
                    if(!Settings.LP.L_infinity) {
                        if(program.getValue(var_pair[0]) > 0.5 || program.getValue(var_pair[1]) > 0.5) counter++;
                    } else {
                        if(program.getValue(var_pair[0]) > 0.5) counter++;
                    }
                }
                lost_adjacencies.put(layer, counter);
            }
        }
        if(Settings.LP.topology_c) {
            for (Integer layer : program.getLayers()) {
                int counter = 0;
                ArrayList<IloNumVar> vars = program.getAdjacencyCounters(layer);
                for (IloNumVar var : vars) {
                    counter += Math.round(program.getValue(var));
                }
                lost_adjacencies.put(layer, counter);
            }
        }

        return lost_adjacencies;
    }

    private HashMap<Integer, HashMap<Integer, Vector>> retrieve_calculated_region_positions(CartogramCplex program) throws IloException {
        if(!program.isSolved()) System.out.println("This program is not yet solved. Retrieving new positions is not meaningful.");
        HashMap<Integer, HashMap<Integer, Vector>> positions = new HashMap<>();
        for (Integer layer : program.getLayers()) {
            System.out.println("layer: " + layer);
            positions.put(layer, program.getRegionPositions(layer));
        }
        return positions;
    }


    public HashMap<Integer, HashMap<Integer, Double>> retrieve_calculated_obstacle_sizes(CartogramCplex program) throws IloException {
        if(!program.isSolved()) System.out.println("This program is not yet solved. Retrieving new sizes is not meaningful.");
        HashMap<Integer, HashMap<Integer, Double>> positions = new HashMap<>();
        for (Integer layer : program.getLayers()) {
            positions.put(layer, program.getObstacleSizes(layer));
        }
        return positions;
    }
}
