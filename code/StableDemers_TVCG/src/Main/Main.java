package Main;

import Force.ForceSolver;
import GUI.DrawPanel;
import GUI.MySidePanel;
import LP.CplexSolver;
import Model.CartogramModel;
import nl.tue.geometrycore.gui.GUIUtil;
import nl.tue.geometrycore.io.ipe.IPEWriter;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

public class Main {

    public static void main(String[] args) throws ParseException, IOException {
        Options opt = getOptions();
        parseBaseArguments(args, opt);

        CartogramModel cartogram = new CartogramModel(Settings.topology_path, Settings.location_path, Settings.weight_path, Settings.stability_path, Settings.ipe_path, Settings.bb_path, Settings.normalization_path);

        Solver solver;
        switch(Settings.solution_method) {
            case "Force":
                parseForceArguments(args, opt);
                solver = new ForceSolver();
                break;
            case "LP":
            default:
                parseLPArguments(args, opt);
                solver = new CplexSolver();
                break;
        }

        // Create the output directory if necessary
        Settings.instance_name = buildInstanceName();
        Settings.out_path += Settings.instance_name + File.separator;
        new File(Settings.out_path).mkdirs();
        System.out.println("Settings Out path: " + Settings.out_path);
//        System.out.println("Instance Name: " + Settings.instance_name);

        // Cartogram is an abstracted model of our instance and its solution
        // Solver is a method to solve this instance, regardless of how it does it
        RunResults result = null;
        try {
            result = solver.buildAndRun(cartogram);
        } catch (Exception e) {
            e.printStackTrace();
        }


        for (int layer = 0; layer < cartogram.getLayerCount(); layer++) {
            cartogram.updateRegionPositions(layer, result.getPositions().get(layer));
            if(Settings.solution_method.equals("LP")){
                cartogram.updateObstacleSizes(layer, result.getSizes().get(layer));
            }
        }
        System.out.println("Updated positions and sizes\n");

        if(Settings.write_eval_file){
            //cartogram.evaluateToFile(Settings.out_path + instance_name + "_eval.json", result);
            cartogram.evaluateToFile(Settings.out_path + Settings.instance_name + "_eval.json", result, false);
            if(Settings.round_output) {
                cartogram.evaluateToFile(Settings.out_path + Settings.instance_name + "_eval_r.json", result, true);
            }
            System.out.println("Evaluated");
        }

        if(Settings.write_region_report) {
            //cartogram.writeRegionReportToFile(Settings.out_path + instance_name + "_regions.json", buildInstanceName());
            cartogram.writeRegionReportToFile(Settings.out_path + Settings.instance_name + "_regions.json");
            System.out.println("Regions written to File");
        }

        if(Settings.GUI || Settings.draw_to_file) {
            System.out.println("Drawing");
            draw(cartogram, Settings.instance_name);
        }

    }

    private static String buildInstanceName() {
        String instance_name = "";
        String[] temp = Settings.location_path.split(Pattern.quote(File.separator));
        instance_name += temp[temp.length-1].split("_")[0];
        temp = Settings.weight_path.split(Pattern.quote(File.separator));
        instance_name += "_"+temp[temp.length-1].split("\\.")[0];
        instance_name += "_"+Settings.solution_method;

        switch(Settings.solution_method) {
            case "LP":
                instance_name += "_";
                if(Settings.LP.disjoint_ilp_c) instance_name += "0";
                if(Settings.LP.disjoint_c) instance_name += "1";
                if(Settings.LP.double_disjont_c) instance_name += "2";
                if(Settings.LP.distance_minimization_c) instance_name += "3";
                if(Settings.LP.angle_nuance_c) instance_name += "4";
                if(Settings.LP.topology_c) instance_name += "5";
                if(Settings.LP.origin_displacement_c) instance_name += "6";
                if(Settings.LP.stability_c){
                    instance_name += "7";
                    temp = Settings.stability_path.split(Pattern.quote(File.separator));
                    String stab_method = temp[temp.length - 1].split("\\.")[0];
                    instance_name += "("+stab_method+")";
                } else {
                    temp = Settings.stability_path.split(Pattern.quote(File.separator));
                    String[] stab_method = temp[temp.length - 1].split("\\.")[0].split("-");
//                    System.out.println(stab_method[stab_method.length-1]);
                    instance_name += "(none-"+stab_method[stab_method.length-1]+")";
                }
                break;
            case "Force":
                //if (!Settings.Force.speed_up.equals("")) instance_name += "_" + Settings.Force.speed_up;
                instance_name += "_";
                if(Settings.Force.disjoint_f) instance_name += "1";
                if(Settings.Force.origin_f) instance_name += "2";
                if(Settings.Force.topological_f) instance_name += "3";
                if(Settings.Force.stability_f){
                    instance_name += "4";
                    temp = Settings.stability_path.split(Pattern.quote(File.separator));
                    String stab_method = temp[temp.length - 1].split("\\.")[0];
                    instance_name += "("+stab_method+")";
                } else {
                    temp = Settings.stability_path.split(Pattern.quote(File.separator));
                    String[] stab_method = temp[temp.length - 1].split("\\.")[0].split("-");
//                    System.out.println(stab_method[stab_method.length-1]);
                    instance_name += "(none-"+stab_method[stab_method.length-1]+")";
                }
                break;
        }

//        temp = Settings.stability_path.split(Pattern.quote(File.separator));
//        instance_name += "_" + temp[temp.length-1].split("\\.")[0];

        return instance_name;
    }

    public static void draw(CartogramModel cartogram, String instance_name) throws IOException {
        DrawPanel dp = new DrawPanel(cartogram);
        MySidePanel sp = new MySidePanel(cartogram.getLayerCount(), dp);
        dp.setSP(sp);
//        IPEWriter write = null;

        if(Settings.GUI) {
            String title = buildInstanceName() + " - " + Settings.solution_method;
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

        if(Settings.draw_to_file) {
            dp.screenshot(Settings.out_path + instance_name + ".ipe");
            System.out.println("Cartogram drawn");
        }

    }

    private static Options getOptions() {
        Options base_options = new Options();

        base_options.addOption("O", "Out", true, "The directory in which output is saved");
        base_options.addOption("T", "Topo", true, "The file containing the topological data");
        base_options.addOption("L", "Location", true, "The file containing the location data");
        base_options.addOption("S", "Stabilities", true, "The file containing the stability information");
        base_options.addOption("W", "Weights", true, "The file containing the weight data");
        base_options.addOption("I", "IPE", true, "The .ipe file containing the map (for second separation). Only one file can be provided an needs to contains ALL regions.");
        base_options.addOption("B", "BB", true, "The .tsv file containing the bounding boxes of the map (for second separation). Different sets of regions per layer SHOULD use this method.");
        base_options.addOption("N", "Normalize", true, "The .tsv file with information which layers are in the same category and should be normalized together. Should include a first line which indicates normalization method, i.e., 'max' or 'sum'");
        base_options.addOption("A", "Approach", true, "The solution method used to create the cartograms");
        base_options.addOption("C", "Continent", false, "Regions are coloured by continent");
        base_options.addOption("R", "Rounding", false, "Rounds the output if set (additional rounded output file)");

        // All of the following three things happen UNLESS flags are set
        base_options.addOption("NoG", "NoGUI", false, "The solution is NOT drawn to a WINDOW. Don't use for headless mode.");
        base_options.addOption("NoI", "NoImageOutput", false, "The solution is NOT drawn to a FILE. Don't use for headless mode.");
        base_options.addOption("NoR", "NoRegionReport", false, "The solution is NOT EXPORTED to a file.");
        base_options.addOption("NoE", "NoEvaluation", false, "The solution is NOT EVALUATED to a file.");
        base_options.addOption("NoM", "NoModelExport", false, "The LP model is NOT EXPORTED to a file.");

        base_options.addOption("lpT", "lpThreads",            true, "The number of cplex threads");

        base_options.addOption("lpC0", "lpDisjointILP",       false,"Enables the Disjointness constraint (hard constraint) without fixed directions. False by default");
        base_options.addOption("lpC1", "lpDisjoint",          false,"Enables the Disjointness constraint (hard constraint) with fixed separation constraints. True by default");
        base_options.addOption("lpC2", "lpDoubleDisjoint",    false,"Enables the Double Disjointness constraint (hard constraint). False by default. Works only if an additional method is provided to derice secondary separation relation.");
        base_options.addOption("lpC3", "lpDistance",          true, "Enables the Distance Minimization constraint (including goal). True by default. Argument is the objective function weight, Factor 1");
        base_options.addOption("lpC4", "lpAngle",             true, "Enables the Angle Nuance constraint (including goal). False by default. Argument is the objective function weight");
        base_options.addOption("lpC5", "lpTopology",          true, "Enables the Topology constraint (including goal). False by default. Argument is the objective function weight");
        base_options.addOption("lpC6", "lpOrigin",            true, "Enables the Origin Displacement constraint (including goal). False by default. Argument is the objective function weight");
        base_options.addOption("lpC7", "lpStability",         true, "Enables the Stability constraint (including goal). False by default. Argument is the objective function weight");

        //base_options.addOption("lpD", "lpDominance",          false,"Enables the use of the dominance relation, in order to cut down on the number of installed disjointness constraints. True by default");
        base_options.addOption("lpLInf", "lpLInfinity",       false,"Enables the use of the L Infinity metric to measure distances. if not set, the default metric is the L1 norm");

        base_options.addOption("lpMinC", "lpMinimalContact",  true, "Length of the minimal contact between regions. The argument is the percentage of the edge length of the smaller region which is required for contact");
        base_options.addOption("lpOL", "lpObstacleLowerBound",  true, "Percentage of the edgelength (NOT AREA) an obstacle is allowed to shrink to");
        base_options.addOption("lpOU", "lpObstacleUpperBound",  true, "Percentage of the edgelength (NOT AREA) an obstacle is allowed to grow to");
        base_options.addOption("lpNuNA", "lpNuanceNotAdjacentFactor",  true, "Factor by which the Nuance constraint is de-emphasized for regions pairs which are not adjacent. Default is 0.1, which should be fine");
        base_options.addOption("lpWarm", "lpMIPWarmStart", false, "Enables MIP Warm start. False by default.");

        base_options.addOption("fIt", "fMaximumIterations",  true, "Maximal number of iterations for the force based approach");
        base_options.addOption("fMM", "fMinimalMovement",true, "Minimal movement distance necessary to qualify for change in iteration. Default is DoubleUtil.EPS, which should be fine.");
        base_options.addOption("fC1", "fDisjoint",true, "Enables disjointness force. True by default. Argument is the scaling factor.");
        base_options.addOption("fC2", "fOrigin",true, "Enables origin force. False by default. Argument is the scaling factor.");
        base_options.addOption("fC3", "fTopology",true, "Enables topology force. True by default. Argument is the scaling factor.");
        base_options.addOption("fC4", "fStability",true, "Enables stability force. False by default. Argument is the scaling factor.");
        base_options.addOption("fSp", "fSpeedUp",true, "Enables the usage of speed up methods for the force directed approach. Argument is the chosen method (Options are 'cross' and 'grid').");
        base_options.addOption("fCo", "fCooling",true, "Sets the cooling factor. Argument is the chosen factor.");

        return base_options;
    }

    private static void parseBaseArguments(String[] args, Options opt) throws ParseException {

        CommandLineParser base_parser = new DefaultParser();
        CommandLine base_cmd = base_parser.parse(opt, args);

        if(base_cmd.hasOption("O")) { Settings.out_path = base_cmd.getOptionValue("O"); }
        if(base_cmd.hasOption("T")) { Settings.topology_path = base_cmd.getOptionValue("T"); }
        if(base_cmd.hasOption("L")) { Settings.location_path = base_cmd.getOptionValue("L"); }
        if(base_cmd.hasOption("S")) { Settings.stability_path = base_cmd.getOptionValue("S"); }
        if(base_cmd.hasOption("W")) { Settings.weight_path = base_cmd.getOptionValue("W"); }
        if(base_cmd.hasOption("I")) { Settings.ipe_path = base_cmd.getOptionValue("I"); }
        if(base_cmd.hasOption("B")) { Settings.bb_path = base_cmd.getOptionValue("B"); }
        if(base_cmd.hasOption("N")) { Settings.normalization_path = base_cmd.getOptionValue("N"); }
        if(base_cmd.hasOption("A")) { Settings.set_solution_method(base_cmd.getOptionValue("A")); }
        Settings.round_output = base_cmd.hasOption("R");
        Settings.GUI = !base_cmd.hasOption("NoG");
        Settings.draw_to_file = !base_cmd.hasOption("NoI");
        Settings.write_region_report = !base_cmd.hasOption("NoR");
        Settings.write_eval_file = !base_cmd.hasOption("NoE");
        Settings.LP.export_LP_model = !base_cmd.hasOption("NoM");
        Settings.colour_by_regions = base_cmd.hasOption("C");
    }

    private static void parseLPArguments(String[] args, Options opt) throws ParseException {

        CommandLineParser lp_parser = new DefaultParser();
        CommandLine lp_cmd = lp_parser.parse(opt, args);

        if(lp_cmd.hasOption("lpT")) { Settings.LP.THREADS = Integer.parseInt(lp_cmd.getOptionValue("lpT")); }

        Settings.LP.disjoint_ilp_c = lp_cmd.hasOption("lpC0");
        Settings.LP.disjoint_c = lp_cmd.hasOption("lpC1");
        Settings.LP.double_disjont_c = lp_cmd.hasOption("lpC2");
        Settings.LP.distance_minimization_c = lp_cmd.hasOption("lpC3");
        Settings.LP.angle_nuance_c = lp_cmd.hasOption("lpC4");
        Settings.LP.topology_c = lp_cmd.hasOption("lpC5");
        Settings.LP.origin_displacement_c = lp_cmd.hasOption("lpC6");
        Settings.LP.stability_c = lp_cmd.hasOption("lpC7");

        if(lp_cmd.hasOption("lpC3")) { Settings.LP.factor_dist_min = Double.parseDouble(lp_cmd.getOptionValue("lpC3")); }
        if(lp_cmd.hasOption("lpC4")) { Settings.LP.factor_angle_nuance = Double.parseDouble(lp_cmd.getOptionValue("lpC4")); }
        if(lp_cmd.hasOption("lpC5")) { Settings.LP.factor_topology = Double.parseDouble(lp_cmd.getOptionValue("lpC5")); }
        if(lp_cmd.hasOption("lpC6")) { Settings.LP.factor_orig_displ = Double.parseDouble(lp_cmd.getOptionValue("lpC6")); }
        if(lp_cmd.hasOption("lpC7")) { Settings.LP.factor_stability = Double.parseDouble(lp_cmd.getOptionValue("lpC7")); }

        //Settings.LP.disjoint_c = lp_options.hasOption("lpD");
        Settings.LP.L_infinity = lp_cmd.hasOption("lpLInf");

        if(lp_cmd.hasOption("lpMinC")) { Settings.LP.min_contact_factor = Double.parseDouble(lp_cmd.getOptionValue("lpMinC")); }
        if(lp_cmd.hasOption("lpOL")) { Settings.LP.obstacle_lower_bound = Double.parseDouble(lp_cmd.getOptionValue("lpOL")); }
        if(lp_cmd.hasOption("lpOU")) { Settings.LP.obstacle_upper_bound = Double.parseDouble(lp_cmd.getOptionValue("lpOU")); }
        if(lp_cmd.hasOption("lpNuNA")) { Settings.LP.nuance_non_adjacent_multiplier = Double.parseDouble(lp_cmd.getOptionValue("lpNuNA")); }
        if(lp_cmd.hasOption("lpWarm")) { Settings.LP.mip_warmstart = true; }
    }

    private static void parseForceArguments(String[] args, Options opt) throws ParseException {

        CommandLineParser force_parser = new DefaultParser();
        CommandLine force_cmd = force_parser.parse(opt, args);

        if(force_cmd.hasOption("fIt")) { Settings.Force.max_iterations = Integer.parseInt(force_cmd.getOptionValue("fIt")); }
        if(force_cmd.hasOption("fMM")) { Settings.Force.minimal_movement = Double.parseDouble(force_cmd.getOptionValue("fMM")); }
        if(force_cmd.hasOption("fSp")) { Settings.Force.speed_up = force_cmd.getOptionValue("fSp"); }
        if(force_cmd.hasOption("fCo")) { Settings.Force.cooling_factor = Double.parseDouble(force_cmd.getOptionValue("fCo")); }

        Settings.Force.disjoint_f = force_cmd.hasOption("fC1");
        Settings.Force.origin_f = force_cmd.hasOption("fC2");
        Settings.Force.topological_f = force_cmd.hasOption("fC3");
        Settings.Force.stability_f = force_cmd.hasOption("fC4");

        if(force_cmd.hasOption("fC1")) { Settings.Force.disjoint_weight = Double.parseDouble(force_cmd.getOptionValue("fC1")); }
        if(force_cmd.hasOption("fC2")) { Settings.Force.origin_weight = Double.parseDouble(force_cmd.getOptionValue("fC2")); }
        if(force_cmd.hasOption("fC3")) { Settings.Force.topological_weight = Double.parseDouble(force_cmd.getOptionValue("fC3")); }
        if(force_cmd.hasOption("fC4")) { Settings.Force.stability_weight = Double.parseDouble(force_cmd.getOptionValue("fC4")); }
    }
}
