# StableDemersLP
Supplementary material (inparticular the source code) for "Computing Stable Demers Cartograms" submitted to TVCG and currently under review.
We provide scripts to setup and run the project for ease of use. This will be outlined first. Below that you can find a detailed explanation of the usage of our program.

Further, we use CPLEX 12.8 as an LP and ILP solver. CPLEX provides free academic licenses, which is in line for the requirements of the Replicability Stamp. However it is not feasible to automate the installation process. We refer to instruction on the IBM webpage (https://www.ibm.com/docs/en/icos/12.8.0.0?topic=cplex-setting-up), explaining how to set up and install CPLEX for java.

Scripts:

First all scripts need to be executed as root and they need to be made executable beforehand.

       chmod +x prepare.sh
       chmod +x run.sh
       
Then one needs to execute prepare.sh
       
       sudo ./prepare.sh
       
This will install Java, git, unzip and clone this repository

Now it is important that the folder StableDemersLP/code/StableDemers_TVCG/lib, contains the CPLEX shared library, which is system dependent. For example, in the case of Linux and CPLEX 12.8, this is libcplex1280.so.
Please copy the appropriate shared library into this folder.
For more information, we refer to IBM's information about paths and jars (https://www.ibm.com/docs/en/icos/12.8.0.0?topic=applications-paths-jars).

Now everything is prepared to run the example execution using:
       
       sudo ./run.sh
       
Note that this loads the data, computes a cartogram and displays it in an interactive GUI, but the instance is hard-coded into run.sh. For computation of other instances, please refer to the detailed description of usage below.


Detailed Usage:


The usage of the code can be found below.

usage: myapp [-A <arg>] [-B <arg>] [-C] [-fC1 <arg>] [-fC2 <arg>] [-fC3
       <arg>] [-fC4 <arg>] [-fCo <arg>] [-fIt <arg>] [-fMM <arg>] [-fSp
       <arg>] [-I <arg>] [-L <arg>] [-lpC0] [-lpC1] [-lpC2] [-lpC3 <arg>]
       [-lpC4 <arg>] [-lpC5 <arg>] [-lpC6 <arg>] [-lpC7 <arg>] [-lpLInf]
       [-lpMinC <arg>] [-lpNuNA <arg>] [-lpOL <arg>] [-lpOU <arg>] [-lpT
       <arg>] [-lpWarm] [-N <arg>] [-NoE] [-NoG] [-NoI] [-NoM] [-NoR] [-O
       <arg>] [-R] [-S <arg>] [-T <arg>] [-W <arg>]

        -A,--Approach <arg>                         The solution method used to
                                                    create the cartograms

        -B,--BB <arg>                               The .tsv file containing the
                                                    bounding boxes of the map
                                                    (for second separation).
                                                    Different sets of regions per
                                                    layer SHOULD use this method.

        -C,--Continent                              Regions are coloured by
                                                    continent

        -fC1,--fDisjoint <arg>                      Enables disjointness force.
                                                    True by default. Argument is
                                                    the scaling factor.

        -fC2,--fOrigin <arg>                        Enables origin force. False
                                                    by default. Argument is the
                                                    scaling factor.

        -fC3,--fTopology <arg>                      Enables topology force. True
                                                    by default. Argument is the
                                                    scaling factor.

        -fC4,--fStability <arg>                     Enables stability force.
                                                    False by default. Argument is
                                                    the scaling factor.

        -fCo,--fCooling <arg>                       Sets the cooling factor.
                                                    Argument is the chosen
                                                    factor.

        -fIt,--fMaximumIterations <arg>             Maximal number of iterations
                                                    for the force based approach.

        -fMM,--fMinimalMovement <arg>               Minimal movement distance
                                                    necessary to qualify for
                                                    change in iteration. Default
                                                    is DoubleUtil.EPS, which
                                                    should be fine.

        -fSp,--fSpeedUp <arg>                       Enables the usage of speed up
                                                    methods for the force
                                                    directed approach. Argument
                                                    is the chosen method (Options
                                                    are 'cross' and 'grid').

        -I,--IPE <arg>                              The .ipe file containing the
                                                    map (for second separation).
                                                    Only one file can be provided
                                                    an needs to contains ALL
                                                    regions.

        -L,--Location <arg>                         The file containing the
                                                    location data.

        -lpC0,--lpDisjointILP                       Enables the Disjointness
                                                    constraint (hard constraint)
                                                    without fixed directions.
                                                    False by default

        -lpC1,--lpDisjoint                          Enables the Disjointness
                                                    constraint (hard constraint)
                                                    with fixed separation
                                                    constraints. True by default

        -lpC2,--lpDoubleDisjoint                    Enables the Double
                                                    Disjointness constraint (hard
                                                    constraint). False by
                                                    default. Works only if an
                                                    additional method is provided
                                                    to derice secondary
                                                    separation relation.

        -lpC3,--lpDistance <arg>                    Enables the Distance
                                                    Minimization constraint
                                                    (including goal). True by
                                                    default. Argument is the
                                                    objective function weight,
                                                    Factor 1

        -lpC4,--lpAngle <arg>                       Enables the Angle Nuance
                                                    constraint (including goal).
                                                    False by default. Argument is
                                                    the objective function weight

        -lpC5,--lpTopology <arg>                    Enables the Topology
                                                    constraint (including goal).
                                                    False by default. Argument is
                                                    the objective function weight

        -lpC6,--lpOrigin <arg>                      Enables the Origin
                                                    Displacement constraint
                                                    (including goal). False by
                                                    default. Argument is the
                                                    objective function weight

        -lpC7,--lpStability <arg>                   Enables the Stability
                                                    constraint (including goal).
                                                    False by default. Argument is
                                                    the objective function weight

        -lpLInf,--lpLInfinity                       Enables the use of the L
                                                    Infinity metric to measure
                                                    distances. if not set, the
                                                    default metric is the L1 norm

        -lpMinC,--lpMinimalContact <arg>            Length of the minimal contact
                                                    between regions. The argument
                                                    is the percentage of the edge
                                                    length of the smaller region
                                                    which is required for contact

        -lpNuNA,--lpNuanceNotAdjacentFactor <arg>   Factor by which the Nuance
                                                    constraint is de-emphasized
                                                    for regions pairs which are
                                                    not adjacent. Default is 0.1,
                                                    which should be fine

        -lpOL,--lpObstacleLowerBound <arg>          Percentage of the edgelength
                                                    (NOT AREA) an obstacle is
                                                    allowed to shrink to

        -lpOU,--lpObstacleUpperBound <arg>          Percentage of the edgelength
                                                    (NOT AREA) an obstacle is
                                                    allowed to grow to

        -lpT,--lpThreads <arg>                      The number of cplex threads

        -lpWarm,--lpMIPWarmStart                    Enables MIP Warm start. False
                                                    by default.

        -N,--Normalize <arg>                        The .tsv file with
                                                    information which layers are
                                                    in the same category and
                                                    should be normalized
                                                    together. Should include a
                                                    first line which indicates
                                                    normalization method, i.e.,
                                                    'max' or 'sum'

        -NoE,--NoEvaluation                         The solution is NOT EVALUATED
                                                    to a file.

        -NoG,--NoGUI                                The solution is NOT drawn to
                                                    a WINDOW. Don't use for
                                                    headless mode.

        -NoI,--NoImageOutput                        The solution is NOT drawn to
                                                    a FILE. Don't use for
                                                    headless mode.

        -NoM,--NoModelExport                        The LP model is NOT EXPORTED
                                                    to a file.

        -NoR,--NoRegionReport                       The solution is NOT EXPORTED
                                                    to a file.

        -O,--Out <arg>                              The directory in which output
                                                    is saved

        -R,--Rounding                               Rounds the output if set
                                                    (additional rounded output
                                                    file)

        -S,--Stabilities <arg>                      The file containing the
                                                    stability information

        -T,--Topo <arg>                             The file containing the
                                                    topological data

        -W,--Weights <arg>                          The file containing the
                                                    weight data

              
One example executions is:
              
          java -Djava.library.path=[path_to_project]/code/StableDemers_TVCG/lib -Xmx8192m -jar [path_to_project]/code/StableDemers_TVCG/StableDemers_TVCG.jar -O [path_to_project]/code/StableDemers_TVCG/testing/outs/ -T [path_to_project]/code/StableDemers_TVCG/data/topo/USA_adjacencies.tsv -L [path_to_project]/code/StableDemers_TVCG/data/locs/USA_locs.tsv -S [path_to_project]/code/StableDemers_TVCG/data/stability/complete-4.tsv -W [path_to_project]/code/StableDemers_TVCG/data/weights/USA/Mixed.tsv -B [path_to_project]/code/StableDemers_TVCG/data/bbs/USA.tsv -N [path_to_project]/code/StableDemers_TVCG/data/norm/Sum-Ind-4.tsv -NoI -NoR -lpC1 -lpC3 1 -lpC7 0.01
              
It is important that the folder, which -Djava.library.path points to, i.e., code/StableDemers_TVCG/lib, contains the CPLEX shared library, which is system dependent. For example, in the case of Linux and CPLEX 12.8, this is libcplex1280.so.
For more information, we refer to IBM's information about paths and jars (https://www.ibm.com/docs/en/icos/12.8.0.0?topic=applications-paths-jars).
