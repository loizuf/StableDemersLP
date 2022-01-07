package Force;

import Main.RunResults;
import Main.Settings;
import Model.CartogramModel;
import Model.Region;
import nl.tue.geometrycore.geometry.Vector;

import java.util.*;

public class ForceSolver_old extends Main.Solver {

    private CartogramModel cartogram;

    private double maxdist;

    HashMap<Integer, HashMap<Integer, Vector>> forces;
    HashMap<Integer, HashMap<Integer, Vector>> output_locations;


    public void setup(CartogramModel c){
        this.cartogram = c;

        forces = new HashMap<>();
        output_locations = new HashMap<>();
        for (int layer = 0; layer < cartogram.getLayerCount(); layer++) {

            int layer_force_index = 0;

            forces.put(layer, new HashMap<>());
            output_locations.put(layer, new HashMap<>());
            for (Region layer_region : cartogram.getRegions(layer)) {

                forces.get(layer).put(layer_region.index, Vector.origin().clone());
                // If we switch to other systems of base spots again, this is where we would initialize different values for the output locations
                output_locations.get(layer).put(layer_region.index, layer_region.getOriginalLocation().clone());
            }
        }
        maxdist = cartogram.getInputBoundingBox().width() + cartogram.getInputBoundingBox().height();
    }

    @Override
    public RunResults run(CartogramModel c, List<Set<Integer>> iterative_order) {

        setup(c);

        double start = System.currentTimeMillis();
        int counter = 0;
        while (counter < Settings.Force.max_iterations) {

            // compute forces
            for (int layer = 0; layer < cartogram.getLayerCount(); layer++) {

                for (Region region : cartogram.getRegions(layer)) {
                    Vector disjoint_force = new Vector(0, 0);
                    if(Settings.Force.disjoint_f) {
                        disjoint_force = applyDisjointForce(region, layer);
                        disjoint_force.scale(Settings.Force.disjoint_weight);
                    }

                    Vector layer_force = new Vector(0, 0);
                    if(Settings.Force.origin_f) {
                        layer_force = applyOriginForce(region, layer);
                        layer_force.scale(Settings.Force.origin_weight);
                    }
                    if(Settings.Force.topological_f) {
                        layer_force = applyTopoForce(region, layer);
                        layer_force.scale(Settings.Force.topological_weight);
                    }

                    Vector stability_force = new Vector(0, 0);
                    if (Settings.Force.stability_f) {
                        stability_force = applyStabilityForce(region, layer);
                        stability_force.scale(Settings.Force.stability_weight);
                    }

                    int additional_forces = 0;
                    if(Settings.Force.origin_f) additional_forces++;
                    if(Settings.Force.topological_f) additional_forces++;
                    if(Settings.Force.stability_f) additional_forces++;

                    // This vector is in range [0, Settings.Force.disjoint_weight + additional_forces]
                    forces.get(layer).put(region.index, Vector.add(Vector.add(disjoint_force, layer_force), stability_force));
                    // Scale it to [0, 1]
                    forces.get(layer).get(region.index).scale(1.0 / (Settings.Force.disjoint_weight + additional_forces));
                    // Scale it further to [0, 0.25 * cartogram.getMinSize()]
                    forces.get(layer).get(region.index).scale(cartogram.getMinSize());
                }
            }

            // apply forces
            boolean didsomething = false;
            double maxForce = 0;
            for (int layer = 0; layer < cartogram.getLayerCount(); layer++) {
                for (Region region : cartogram.getRegions(layer)) {
                    output_locations.get(layer).get(region.index).translate(
                            forces.get(layer).get(region.index));
                    if(forces.get(layer).get(region.index).length() > Settings.Force.minimal_movement) {
                        if (forces.get(layer).get(region.index).length() > maxForce) maxForce = forces.get(layer).get(region.index).length();
                        didsomething = true;
                    }
                }
            }
            if (!didsomething) {
                break;
            }
            //if (counter%100==0) {
                System.out.println("Its remaining: " + (Settings.Force.max_iterations - counter));
                System.out.println("Biggest Force is at " + maxForce);
            //}
            //System.out.println(output_locations);
            counter += 1;
        }

        RunResults results = new RunResults();
        results.addRuntime(System.currentTimeMillis() - start);
        results.addPositions(output_locations);


        // Note that our Force-directed approach does not have the concept of placeholder regions

        return results;
    }

    private Vector applyDisjointForce(Region r, int layer) {
        //System.out.println("calling disjoint with id: " + id + " and layer: "+ layer);
        //System.out.println(outputLocations.size());
        Vector loc_r = output_locations.get(layer).get(r.index);
        Vector disjoint_force = Vector.origin();
        for (Region s : cartogram.getRegions(layer)) {
            if (r.index == s.index) continue;
            Vector loc_s = output_locations.get(layer).get(s.index);
            boolean adjacent = cartogram.adjacent(r, s, layer);

            Vector diff = Vector.subtract(loc_r, loc_s);
            double Linf = Math.max(Math.abs(diff.getX()), Math.abs(diff.getY()));
            double target = (r.getSize(layer) + s.getSize(layer))/2.0 + (adjacent ? 0 : cartogram.getMinSize());
            if(Linf < target) {
                double d = (target - Linf) / target;
                if (diff.isApproximately(Vector.origin())) {
                    diff.translate(0.0001, 0.0001);
                }
                diff.normalize();
                diff.scale(d * d);
                disjoint_force.translate(diff);
            }
        }
        return disjoint_force;
    }

    private Vector applyOriginForce(Region r, int layer) {

        Vector or = r.getOriginalLocation();
        Vector loc = output_locations.get(layer).get(r.index);

        Vector diff = Vector.subtract(or, loc);
        diff.scale(1.0 / maxdist);
        return diff;
    }

    private Vector applyTopoForce(Region r, int layer) {

        Vector topological_force = new Vector(0,0);

        Vector loc_r = output_locations.get(layer).get(r.index);
        Set<Region> neighbours = cartogram.getNeighbours(r, layer);
        if (neighbours.size() != 0) {
            for (Region s : neighbours) {
                Vector loc_s = output_locations.get(layer).get(s.index);

                Vector diff = Vector.subtract(loc_s, loc_r);
                double Linf = Math.max(Math.abs(diff.getX()), Math.abs(diff.getY()));
                double target = (r.getSize(layer) + s.getSize(layer))/2.0;
                //System.out.println(layer + "|"+r.code +": "+ diff + ", " +Linf);

                if(Linf > target) {
                    double d = Linf - target;
                    diff.scale(d / Linf);
                    topological_force.translate(diff);
                }
            }
            topological_force.scale(1.0 / (neighbours.size()));
        }
        return topological_force;
    }

    private Vector applyStabilityForce(Region r, int layer) {
        Vector loc_r = output_locations.get(layer).get(r.index);
        Vector f = Vector.origin();
        for (int l = 0; l < cartogram.getLayerCount(); l++) {
            if (l == layer || !cartogram.isStableTo(layer, l))
                continue;
            Vector loc_s = output_locations.get(l).get(r.index);
            Vector diff = Vector.subtract(loc_r, loc_s);
            f = Vector.add(diff, f);
        }
        f.scale(1.0 / (maxdist*cartogram.getNumberOfStableLayers(layer)));
        return f;
    }

}
