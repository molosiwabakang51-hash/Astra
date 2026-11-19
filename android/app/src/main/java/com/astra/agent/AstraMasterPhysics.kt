package com.astra.agent

/** Original offline physics workshop material with transparent idealized assumptions. */
internal object AstraMasterPhysics {
    val units: List<AstraMasterclass.Unit> = listOf(
        AstraMasterclass.Unit(
            "Physical quantities, SI units and uncertainty", "Foundation",
            "Physics tests quantitative models against measurements. A physical quantity combines a numerical value with a unit; dimensional analysis checks that equations use compatible quantities. SI base units include metre, second, kilogram, ampere and kelvin. Accuracy is closeness to an accepted reference, while precision concerns repeatability; systematic errors may be precise but inaccurate. Instruments have resolution and calibration limits. Report an appropriate number of significant figures and distinguish measured uncertainty from variability in repeated trials. A calculated result should preserve units and state assumptions instead of implying exactness.",
            "A ruler reads 1.25 m with an estimated uncertainty ±0.01 m. Another length is 0.75 m ±0.01 m. Their sum is 2.00 m; a simple conservative bound on combined absolute uncertainty is ±0.02 m. Reporting 2.000000 m would imply unjustified precision.",
            "Measure five lengths or time intervals, recording the instrument and resolution. Calculate a mean and sample spread, identify possible zero offset, and check that each equation has compatible units. Explain how you would improve measurement quality without pretending uncertainty disappears.",
            "Which expression has dimensions of speed?",
            listOf("metres per second", "newtons times seconds", "kilograms per metre"), 'A',
            "Speed is distance divided by time, so its SI derived unit is m/s."
        ),
        AstraMasterclass.Unit(
            "Displacement, velocity and acceleration", "Foundation",
            "Position specifies location relative to a reference. Displacement is the change in position, while distance is total path length; they need not be equal. Average velocity is displacement divided by elapsed time and includes direction, whereas average speed uses distance. Acceleration is the time rate of change of velocity. Equations such as v = u + at and s = ut + 0.5at² require approximately constant acceleration. Negative acceleration need not mean slowing down unless its direction opposes the velocity. Sketching position–time and velocity–time graphs often reveals conceptual mistakes.",
            "A cyclist moves 30 m east then 10 m west in 20 s. Distance travelled is 40 m, but displacement is 20 m east. Average speed is 2 m/s; average velocity is 1 m/s east. These differ even though they describe the same journey.",
            "Create a three-stage journey, recording position and time at each stage. Calculate total distance, displacement, average speed and velocity. Sketch both position–time and velocity–time graphs and explain why their slopes convey different information.",
            "After moving 30 m east and 10 m west, what is the displacement?",
            listOf("40 m east", "20 m east", "20 m west"), 'B',
            "Displacements add with direction: +30 m − 10 m = +20 m east."
        ),
        AstraMasterclass.Unit(
            "Vectors, forces and Newton's laws", "Foundation",
            "Force is a vector: magnitude and direction matter. Newton's first law states that in an inertial frame an object maintains constant velocity when net external force is zero. The second law in the constant-mass form is ΣF = ma. The third law relates forces in pairs acting on different interacting objects, not two forces cancelling on the same body. Draw a free-body diagram before choosing axes. Weight near Earth's surface is approximately mg, whereas mass is an intrinsic quantity measured in kilograms. Contact forces, tension and friction require separate physical models.",
            "A 3 kg trolley experiences a net horizontal force of 12 N on a level idealized track. Its acceleration is 12/3 = 4 m/s². If a second 4 N force acts in the opposite direction, the net becomes 8 N and the acceleration falls to about 2.67 m/s².",
            "Draw free-body diagrams for a box on a table, an elevator accelerating upward and a hanging mass. Resolve all forces along chosen axes, calculate a net force and state why the third-law partner of weight is not the table's normal force.",
            "What is the acceleration of a 2 kg object under 10 N net force?",
            listOf("20 m/s²", "5 m/s²", "0.2 m/s²"), 'B',
            "Newton's second law gives a=F/m=10/2=5 m/s²."
        ),
        AstraMasterclass.Unit(
            "Friction, drag and circular motion", "Intermediate",
            "Static friction can adjust up to an approximate maximum μsN, while kinetic friction is often modelled as μkN for sliding contact. These textbook coefficients are approximations; surface conditions and speed may change behavior. Drag from air or water can depend on speed, shape and flow regime. Uniform circular motion keeps speed constant but continually changes velocity direction, requiring centripetal acceleration v²/r directed toward the centre. A centripetal force is the net inward force supplied by actual interactions such as friction, tension or gravity, not an additional new category of force.",
            "A 1,000 kg vehicle travels around a level circular curve of radius 50 m at 10 m/s. The required inward force is m v²/r = 1,000×100/50 = 2,000 N. Whether tires can supply it depends on normal force, road conditions and a suitable friction model.",
            "For a block of mass 5 kg on a horizontal surface with μk=0.20, estimate kinetic friction using g=9.8 m/s². Then compare a circular curve at 10 m/s and 20 m/s, holding radius fixed, and explain why required centripetal force quadruples.",
            "At constant radius, doubling speed changes centripetal acceleration by:",
            listOf("A factor of two", "No change", "A factor of four"), 'C',
            "Since a=v²/r, doubling v multiplies a by four."
        ),
        AstraMasterclass.Unit(
            "Work, kinetic energy and potential energy", "Intermediate",
            "Mechanical work is energy transferred by a force acting through a displacement, W = Fd cosθ for a constant force. Kinetic energy is 0.5mv²; gravitational potential energy near the Earth's surface changes by mgΔh. The work–energy theorem relates net work to change in kinetic energy. In an ideal system with only conservative forces, mechanical energy is conserved, but friction and other dissipative processes transfer energy into heat or other forms. Energy itself is conserved more generally, so 'lost energy' usually means converted energy, not disappearance. Always specify system boundaries.",
            "An ideal 2 kg object accelerates from rest to 3 m/s. Its kinetic energy gain is 0.5×2×9 = 9 J. A constant net force of 3 N acting along displacement would require 3 m to supply 9 J of work, assuming no other energy changes.",
            "Calculate the gravitational potential energy change for a 4 kg mass raised 2.5 m with g=9.8 m/s². Estimate speed after an ideal frictionless fall from that height. Then add a hypothetical 15 J of energy dissipated and explain the revised energy accounting.",
            "What is the kinetic energy of a 2 kg object moving at 3 m/s?",
            listOf("9 J", "3 J", "18 J"), 'A',
            "KE=0.5mv²=0.5×2×3²=9 joules."
        ),
        AstraMasterclass.Unit(
            "Linear momentum, collisions and impulse", "Intermediate",
            "Linear momentum p=mv is a vector. An impulse J=∫Fdt equals a system's change in momentum, and for a constant force J=FΔt. Total momentum is conserved for an isolated system with negligible net external impulse. Kinetic energy is conserved in ideal elastic collisions but not generally in inelastic collisions. In a perfectly inelastic collision objects move together after impact; energy may be transformed into deformation, heat or sound. Safety devices reduce force by lengthening collision time for a given momentum change, but real dynamics may include complex force histories.",
            "A 1 kg trolley moving right at 4 m/s sticks to a stationary 3 kg trolley on a frictionless track. Conservation of momentum gives (1×4+3×0)/(1+3)=1 m/s right afterward. Initial kinetic energy is 8 J, final kinetic energy is 2 J, so 6 J becomes other forms.",
            "Calculate velocities for a perfectly inelastic collision of two masses you choose with opposing directions. Draw a positive-axis convention, compare momentum before and after, and account for the mechanical energy difference. Explain why equal and opposite internal forces do not imply zero individual impulses.",
            "What is always conserved in an isolated ideal collision?",
            listOf("Kinetic energy", "Total momentum", "Each object's velocity"), 'B',
            "Momentum conservation follows from negligible external impulse; kinetic energy may change."
        ),
        AstraMasterclass.Unit(
            "Torque, equilibrium and angular momentum", "Intermediate",
            "Torque measures a force's rotational tendency about a chosen axis and is τ=rF sinθ. Its sign depends on the rotation convention. Static rotational equilibrium requires net torque zero alongside net translational force zero. For a rigid body rotating about a fixed axis, τnet=Iα, where I is moment of inertia and α is angular acceleration. Angular momentum is conserved when external torque is negligible; a figure skater can spin faster by reducing rotational inertia. Torque, angular momentum and work are different physical quantities even though their formulas can look related.",
            "A 10 N force acts perpendicular to a wrench at a distance 0.25 m from the bolt. The torque magnitude is 2.5 N·m. Moving the force to 0.10 m from the bolt reduces torque to 1.0 N·m. A force parallel to the wrench contributes no torque about the bolt.",
            "Balance a 1 m beam on a pivot with two known weights at different distances. Draw torques using a sign convention, calculate the missing force and explain the difference between balancing forces and balancing moments. Repeat with a nonperpendicular applied force.",
            "What is torque from 10 N applied perpendicularly at 0.25 m?",
            listOf("40 N·m", "10.25 N·m", "2.5 N·m"), 'C',
            "The torque magnitude is τ=rF=0.25×10=2.5 N·m."
        ),
        AstraMasterclass.Unit(
            "Gravity, fields and orbital motion", "Intermediate",
            "Newton's law of gravitation models attraction between masses as F=Gm1m2/r² for point masses or spherically symmetric mass distributions outside the body. Gravitational field strength varies with distance; the near-surface approximation g≈9.8 m/s² is not universal. An orbiting satellite remains in free fall with its path continually deflected by gravity. For an ideal circular orbit around a much heavier central mass, speed is √(GM/r); real orbits can be elliptical and affected by other bodies. Weightlessness in orbit is not the absence of gravity. Distinguish force, potential and orbital energy.",
            "If the centre-to-centre separation between two point masses doubles while their masses remain fixed, gravitational force becomes one quarter. Near Earth a 2 kg object has approximate weight 19.6 N using g=9.8, although weight changes slightly with altitude and latitude.",
            "Compare gravitational force at distances r, 2r and 3r between two fixed point masses. Sketch a satellite orbit and show the inward acceleration vector at three positions. Explain why astronauts in low Earth orbit are not beyond Earth's gravitational influence.",
            "At double the separation, the gravitational force is:",
            listOf("One quarter", "One half", "Four times as great"), 'A',
            "The inverse-square dependence gives F∝1/r², so doubling distance divides force by four."
        ),
        AstraMasterclass.Unit(
            "Fluids, pressure and buoyancy", "Intermediate",
            "Fluid pressure is force per unit area, and hydrostatic pressure in an approximately incompressible liquid increases with depth by ρgh. Pascal's principle concerns pressure transmission in confined fluids. Archimedes' principle states that buoyant force equals the weight of displaced fluid, not automatically the weight of the object. Flow continuity follows mass conservation; under restricted steady, incompressible, inviscid assumptions Bernoulli's equation relates pressure, speed and height. Real fluid systems experience viscosity, turbulence and pressure losses. A pressure equation must not be applied blindly across a moving pump or a dissipative pipe.",
            "For water density about 1,000 kg/m³, g=9.8 m/s² and depth 2 m, the gauge hydrostatic pressure is approximately 1,000×9.8×2=19,600 Pa. Absolute pressure adds atmospheric pressure. A submerged object displacing 0.001 m³ experiences about 9.8 N of buoyant force in this ideal water model.",
            "Calculate gauge pressure at 1, 3 and 5 m depth in still water. Sketch a buoyancy diagram for a floating wooden block, showing weight and upward force. Explain the assumptions behind Bernoulli flow and when frictional head loss is important.",
            "What determines the buoyant force on a fully submerged object?",
            listOf("Its color", "The weight of fluid displaced", "Only the mass of the object"), 'B',
            "Archimedes' principle ties buoyancy to the surrounding fluid's displaced weight."
        ),
        AstraMasterclass.Unit(
            "Temperature, heat and the first law", "Intermediate",
            "Temperature is related to thermal state, but heat is energy transfer driven by temperature differences; heat is not stored as a substance inside an object. Internal energy includes microscopic kinetic and interaction energy. Using the common convention that W is work done by a system, the first law is ΔU=Q−W. For an ideal gas, the equation of state PV=nRT connects pressure, volume and absolute temperature, subject to model limits. Phase changes can absorb energy while temperature remains nearly constant. When solving thermodynamics problems specify system, process, sign convention and whether heat and work cross the boundary.",
            "A gas receives 500 J of heat and performs 200 J of work on its surroundings. With ΔU=Q−W, internal energy increases by 300 J. If work is instead defined as work done on the gas, the algebraic sign convention changes; both can be consistent when used correctly.",
            "Describe a closed piston–cylinder process in which the gas absorbs heat and expands. Draw an energy-flow diagram and calculate ΔU for Q=800 J and W=350 J. Repeat for compression and state clearly which work sign convention you use.",
            "With ΔU=Q−W, what if Q=500 J and work by gas is 200 J?",
            listOf("+300 J", "−300 J", "+700 J"), 'A',
            "The first law gives ΔU=500−200=300 joules."
        ),
        AstraMasterclass.Unit(
            "Entropy, irreversible processes and engines", "Advanced",
            "The second law constrains the direction of spontaneous processes. Entropy is a state variable whose total change for an isolated system does not decrease in a real spontaneous process. Heat can move from hot to cold without external work, while moving heat from cold to hot requires a suitable work input or coupling. No cyclic heat engine can convert all heat input from one thermal reservoir into useful work; efficiency is bounded by reservoir temperatures. For ideal reversible Carnot operation between absolute temperatures Th and Tc, maximum efficiency is 1−Tc/Th. Real machines also experience friction, finite temperature differences and other irreversibilities.",
            "An ideal Carnot engine operates between 600 K and 300 K. Its theoretical efficiency upper bound is 1−300/600=50%. That does not mean a physical engine will achieve 50%; losses and practical constraints usually reduce efficiency. Kelvin values, not Celsius values, belong in the ratio.",
            "Compare Carnot bounds at 500 K/300 K and 800 K/300 K. Then list three mechanisms that make a real engine less efficient, and explain why friction can increase entropy production rather than merely 'remove' energy.",
            "What is the ideal Carnot efficiency for Th=600 K and Tc=300 K?",
            listOf("25%", "50%", "100%"), 'B',
            "η=1−Tc/Th=1−300/600=0.5, an ideal upper bound."
        ),
        AstraMasterclass.Unit(
            "Oscillations, resonance and restoring forces", "Intermediate",
            "Simple harmonic motion occurs when an appropriate restoring force is proportional and opposite to displacement: F=−kx for an ideal spring. Its displacement varies sinusoidally, with angular frequency ω=√(k/m) and period T=2π√(m/k) for a mass–spring oscillator. Real oscillations can be damped by friction or other losses, and a driving force can cause resonance near characteristic frequencies. Resonance response depends on damping and drive; it does not imply energy appears from nowhere. Small-angle pendulum approximations and spring models have domains of validity that must be checked.",
            "An ideal mass of 1 kg attached to a spring with k=100 N/m has ω=√100=10 rad/s and T=2π/10≈0.628 s. Doubling mass increases period by √2, not by a factor of two, if the spring constant remains unchanged.",
            "Measure or simulate the period of an oscillator at three masses using the same spring. Plot T² versus mass and estimate whether the relationship is linear. Explain how damping and measurement noise would affect your experimental plot.",
            "With spring constant fixed, the period of an ideal mass–spring oscillator varies as:",
            listOf("m", "1/m", "the square root of m"), 'C',
            "T=2π√(m/k), so period is proportional to √m when k stays constant."
        ),
        AstraMasterclass.Unit(
            "Waves, interference and sound", "Intermediate",
            "A wave transmits disturbances and energy through a medium or field; individual material particles do not generally travel with a wave across the full propagation distance. Wavelength λ, frequency f and wave speed v are related by v=fλ in a uniform medium. Interference results from superposition of coherent waves, with constructive or destructive combinations depending on phase. Sound is a longitudinal mechanical wave in gases and cannot propagate through vacuum, while light can. Pitch relates primarily to frequency, loudness relates to intensity and perception, and Doppler shifts arise from relative motion.",
            "If a wave travels at 300 m/s with frequency 150 Hz, its wavelength is λ=v/f=2 m. Doubling frequency while holding speed constant halves wavelength. This is a property of the given medium, not a promise that the source emits constant energy.",
            "Draw two wave trains with equal amplitude and different phase shifts. Calculate wavelengths for frequencies 100, 200 and 500 Hz using one assumed speed. Design an observation that distinguishes constructive interference from two unrelated noises.",
            "A wave travels at 300 m/s and has frequency 150 Hz; its wavelength is:",
            listOf("2 m", "0.5 m", "450 m"), 'A',
            "The relation v=fλ gives λ=300/150=2 metres."
        ),
        AstraMasterclass.Unit(
            "Reflection, refraction and geometric optics", "Intermediate",
            "Light changes direction at boundaries because its speed differs between media, subject to optical properties. The law of reflection gives equal incident and reflected angles measured from the normal. Refraction obeys Snell's law n1 sinθ1=n2 sinθ2 for simple isotropic media. A thin converging lens approximately obeys 1/f=1/do+1/di with a consistent sign convention. Focal length, object distance and image distance must be interpreted using the same sign system. Real optical instruments suffer aberrations and have wavelength-dependent dispersion. A ray diagram can show qualitatively what an image formula predicts.",
            "With a thin converging lens of focal length 10 cm and an object 30 cm away, 1/di=1/10−1/30=2/30, so di=15 cm in the standard simple positive-distance convention. Magnification is −di/do=−0.5, indicating an inverted image half the object's height.",
            "Draw principal rays for a converging lens with an object outside twice the focal length and then between f and 2f. Calculate image positions for object distances 15, 30 and 50 cm at f=10 cm. Explain the limitations of the thin-lens approximation.",
            "For f=10 cm and object distance 30 cm, thin-lens image distance is:",
            listOf("30 cm", "15 cm", "5 cm"), 'B',
            "1/di=1/10−1/30=1/15, so the simple image distance is 15 cm."
        ),
        AstraMasterclass.Unit(
            "Electric charge, fields and circuits", "Intermediate",
            "Electric charge is conserved, and an electric field is force per unit positive test charge. Voltage is electric potential difference, measured as energy per unit charge. Electric current is charge flow per unit time. Ohm's law V=IR describes suitable ohmic components under appropriate conditions; not all devices have a constant resistance. Series circuits carry the same current through each element, while parallel branches share the same voltage. Electrical power is P=VI, also I²R or V²/R for resistive elements. A circuit model must identify sources, resistors, meters, polarity and safe measurement practices.",
            "With a 12 V ideal source and a 4 Ω resistor, current is I=12/4=3 A and power dissipated is P=VI=36 W. For two identical 4 Ω resistors in series, total resistance is 8 Ω and ideal current becomes 1.5 A. Real sources and components may heat or have resistance changes.",
            "Construct symbolic series and parallel circuit diagrams using two resistors. Calculate total resistance, branch current and total power for a 9 V source. Explain why placing an ammeter across a battery can be unsafe and how meters should be connected.",
            "What current flows through an ideal 4 Ω resistor across 12 V?",
            listOf("48 A", "0.33 A", "3 A"), 'C',
            "Ohm's law gives I=V/R=12/4=3 amperes."
        ),
        AstraMasterclass.Unit(
            "Magnetism, motors and induction", "Intermediate",
            "Magnetic fields exert forces on moving charges and currents, with direction determined by vectors and the right-hand rule for positive charges. A stationary charge in a purely magnetic field experiences no magnetic Lorentz force. A changing magnetic flux can induce an electromotive force according to Faraday's law; Lenz's law describes the induced direction opposing the change that produced it. Generators and transformers use electromagnetic induction, while motors use forces on current-carrying conductors. A transformer requires time-varying flux and cannot simply raise the voltage of steady direct current without switching circuitry.",
            "If flux through a one-turn loop changes by 0.02 weber in 0.10 seconds, the average induced EMF magnitude is |ΔΦ/Δt| = 0.2 V. The sign depends on orientation and the sign convention. Ten otherwise identical turns would give about ten times the EMF in this simplified case.",
            "Sketch a conducting loop near a moving bar magnet and identify the direction of changing flux. Calculate average EMF for two flux-change intervals and compare a single-turn coil with 20 turns. State a physical reason transformer heating may exceed the ideal model.",
            "Which change can induce an EMF in a conducting loop?",
            listOf("Changing magnetic flux through the loop", "Holding all magnetic conditions fixed", "Placing a stationary neutral insulator nearby"), 'A',
            "Faraday's law connects induced EMF to the time rate of change of magnetic flux."
        ),
        AstraMasterclass.Unit(
            "Atoms, spectra and quantum ideas", "Advanced",
            "Classical mechanics cannot explain all microscopic observations. Quantized energy levels in atoms produce characteristic absorption and emission lines. Planck's relation E=hf associates photon energy with frequency; the photoelectric effect illustrates that electron emission depends on photon energy relative to a work-function threshold rather than light intensity alone. The Schrödinger equation predicts probability amplitudes rather than exact classical trajectories. Quantum uncertainty concerns the limits of joint measurement of noncommuting quantities, not merely instrument imperfections. Keep the simple models distinct from full quantum field descriptions.",
            "A photon of frequency 5×10^14 Hz has energy E≈6.626×10^−34×5×10^14≈3.31×10^−19 J. Doubling photon frequency doubles energy per photon, while doubling intensity at fixed frequency increases photon rate in a typical beam, not the energy of each photon.",
            "Calculate photon energies for three visible-light frequencies using Planck's constant. Draw a conceptual photoelectric graph of maximum electron kinetic energy versus frequency, identifying a threshold. Explain why a low-frequency high-intensity beam may fail to eject electrons from a material.",
            "What happens to a photon's energy when its frequency doubles?",
            listOf("It doubles", "It halves", "It stays constant"), 'A',
            "The relation E=hf is linear in frequency for each photon."
        ),
        AstraMasterclass.Unit(
            "Special relativity and reference frames", "Advanced",
            "Special relativity concerns measurements between inertial observers and is based on the equivalence of physical laws in inertial frames and the invariant vacuum speed of light. Events simultaneous in one inertial frame need not be simultaneous in another. Time dilation and length contraction follow Lorentz transformations; these are quantitative relations, not the claim that clocks malfunction. For relative speed v, γ=1/√(1−v²/c²), which approaches one for speeds much smaller than c. No massive object can be accelerated through the speed of light with finite energy. General relativity extends the picture to gravitation and curved spacetime.",
            "At v=0.6c, γ=1/√(1−0.36)=1/0.8=1.25. A clock moving at that speed relative to an inertial observer accumulates less elapsed coordinate-time on the path than the observer's corresponding measurement under the standard comparison setup. This does not mean either local clock is physically defective.",
            "Calculate γ at speeds 0, 0.3c, 0.6c and 0.8c. Explain which observer is making which measurement when describing a moving clock. Sketch a simple spacetime diagram and identify why Newtonian simultaneity fails.",
            "At v=0.6c, the Lorentz factor is:",
            listOf("0.8", "1.25", "2.5"), 'B',
            "γ=1/√(1−0.6²)=1/0.8=1.25."
        ),
        AstraMasterclass.Unit(
            "Experiments, model testing and scientific integrity", "Advanced",
            "A credible physical model produces predictions that observations can potentially contradict. Separate a hypothesis, measurement procedure, calibration record, uncertainty budget, fitted parameters and interpretation. Repeating measurements addresses random variation; using an independent method can reveal systematic bias. Correlation does not alone establish causal mechanism, and a small residual does not validate every assumption. Scientific integrity requires documenting failed experiments, data exclusions and model limits, not only attractive results. Safety controls matter before any electrical, mechanical, chemical or optical demonstration.",
            "Suppose a student measures acceleration as 1.8, 1.9, 2.0 and 2.1 m/s² while the theoretical model predicts 2.0 m/s². The mean is 1.95 m/s². A result close to 2.0 does not establish correctness if the timer has systematic bias or the sample is too small. Report the apparatus and uncertainty rather than an absolute claim.",
            "Design a safe experiment to test an object rolling down an inclined plane. Specify hypothesis, independent and dependent variables, measurement resolution, repeated trials and a possible systematic error. Present both results that agree and results that contradict the model.",
            "Which improves the reliability of a physics experiment?",
            listOf("Discarding observations that disagree with theory", "Reporting only the smallest error", "Documenting calibration, uncertainty and repeatable methods"), 'C',
            "Transparent methods and uncertainty analysis make results more reproducible and credible."
        )
    )
}
