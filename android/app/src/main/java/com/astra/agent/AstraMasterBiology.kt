package com.astra.agent

/**
 * Original offline Biology workshop curriculum.
 * For education, not clinical diagnosis, medical advice or a laboratory safety certification.
 */
internal object AstraMasterBiology {
    val units: List<AstraMasterclass.Unit> = listOf(
        AstraMasterclass.Unit(
            "Biology as an evidence-based science", "Foundation",
            "Biology examines living systems using observation, controlled comparison, measurement and explanations that can be revised. A hypothesis produces testable predictions, while a theory integrates a broad body of evidence; theory does not mean an unsupported guess. Replication and independent validation matter because biological variation can masquerade as a treatment effect. Distinguish descriptive observations from claims about mechanism or cause, and specify a suitable control group before collecting data.",
            "Suppose seedlings under blue light average 12 cm in a week and those under white light average 15 cm. The result establishes a difference in these samples, not automatically that light color caused it. Plant species, temperature, water, light intensity, sample size and random allocation all influence interpretation.",
            "Design a safe seedling experiment comparing two light treatments. State the predicted outcome, manipulated variable, measured response, three controls and how you would randomize pots. Report a hypothetical null result without changing your hypothesis afterward.",
            "Which step best supports a causal biological test?",
            listOf("Using a treatment group without a control", "Controlling confounders and randomly allocating comparable samples", "Choosing only the tallest seedlings"), 'B',
            "Comparable controls and appropriate randomization reduce alternative explanations for a difference."
        ),
        AstraMasterclass.Unit(
            "Water, carbon and biomolecular structure", "Foundation",
            "Water's polarity and hydrogen bonding help explain cohesion, solvent behavior and high heat capacity. Biological macromolecules include carbohydrates, lipids, proteins and nucleic acids, but their functions depend on specific structures rather than merely their names. Amino-acid sequence influences protein folding; lipids help build membranes and store energy, while nucleic acids carry genetic information. A biochemical reaction obeys conservation of matter, so do not describe nutrients as material appearing from nothing.",
            "A molecule of glucose has formula C6H12O6. In aerobic cellular respiration, the balanced summary C6H12O6 + 6O2 → 6CO2 + 6H2O transfers chemical energy through multiple pathways. The equation is a net summary, not a single enzyme-catalyzed reaction.",
            "Create a comparison table for water, carbohydrate, triglyceride, protein and DNA: components, a structural characteristic and a cellular function. Explain how protein denaturation may change function without destroying every peptide bond.",
            "Which biomolecule is built from amino-acid subunits?",
            listOf("Protein", "DNA", "Triglyceride"), 'A',
            "Proteins consist of amino-acid residues linked by peptide bonds."
        ),
        AstraMasterclass.Unit(
            "Cell structure and compartmentalization", "Foundation",
            "Cells are the basic structural and functional units of known organisms. Prokaryotes generally lack a membrane-bound nucleus, whereas eukaryotic cells contain compartments such as a nucleus, endomembrane system and mitochondria. Organelles coordinate specialized functions, but not every organism or mature cell has every organelle; mammalian red blood cells lose their nucleus. Surface-area-to-volume relationships constrain exchange in cells, motivating thin membranes and specialized shapes. Microscopy uses resolution and specimen preparation to distinguish observations from artifacts.",
            "A spherical cell with radius r has surface area 4πr² and volume 4πr³/3, so surface-area-to-volume ratio equals 3/r. Doubling radius halves that ratio. A larger cell can therefore face increased transport demands relative to its external membrane area.",
            "Draw a labeled plant cell, animal cell and typical bacterium. Explain one function for the nucleus, mitochondrion, ribosome, cell wall and plasma membrane, and calculate how spherical surface-area-to-volume ratio changes when radius triples.",
            "Which structure occurs in all cellular life?",
            listOf("Membrane-bound nucleus", "Plasma membrane", "Chloroplast"), 'B',
            "All known cells have a plasma membrane, while nuclei and chloroplasts are restricted to particular groups."
        ),
        AstraMasterclass.Unit(
            "Membrane transport, osmosis and gradients", "Intermediate",
            "Plasma membranes are dynamic lipid bilayers with selective transport proteins. Passive diffusion moves substances down an appropriate chemical potential gradient; facilitated diffusion uses proteins but does not by itself consume ATP. Active transport couples movement against a gradient to an energy source. Osmosis is net water movement across a selectively permeable boundary toward conditions of lower water potential; concentration shorthand can obscure solute and pressure effects. Cells maintain homeostasis with coordinated channels, pumps and signaling.",
            "A red blood cell placed in a markedly hypotonic solution may take up water and swell because external solute concentration is lower under ideal assumptions. In a hypertonic solution it can lose water and shrink. A membrane ion pump may maintain the gradients that make later passive ion flow possible.",
            "Sketch solute and water movement for cells in isotonic, hypertonic and hypotonic solutions. Compare a membrane channel and an ATP-driven pump; specify whether transport follows or opposes the relevant gradient, not merely whether a protein is involved.",
            "Which process requires energy to move solute uphill against its electrochemical gradient?",
            listOf("Simple diffusion", "Active transport", "Osmosis alone"), 'B',
            "Active transport must be coupled to an energy source when moving solutes against the relevant gradient."
        ),
        AstraMasterclass.Unit(
            "Enzymes, activation energy and metabolic pathways", "Intermediate",
            "Enzymes catalyze reactions by lowering the activation free-energy barrier without changing the reaction's overall equilibrium constant. Substrate binding, active-site geometry, temperature, pH and inhibitors influence rate. Enzyme saturation means a reaction rate can approach a maximum as substrate concentration increases under a fixed enzyme amount. Metabolic pathways couple reactions, regulate flux and recycle cofactors; inhibition at one step may change concentrations elsewhere. Avoid treating an enzyme as permanently consumed or as a source of energy.",
            "An enzyme converts substrate S to product P. Raising substrate concentration may initially increase the rate, but once most active sites are occupied the rate approaches a maximum under a simple model. Heating can increase collision frequency within limits but can also denature enzyme structure.",
            "Construct a conceptual rate-versus-substrate graph with and without competitive inhibitor. Label the saturation region, describe an experiment at two pH conditions and explain why enzymes change reaction speed but do not make unfavorable equilibria favorable by themselves.",
            "Why do enzymes increase reaction rate?",
            listOf("They change the activation barrier", "They change every product's atomic composition", "They always increase the equilibrium constant"), 'A',
            "Biological catalysts lower activation free-energy barriers while the reaction equilibrium remains governed by thermodynamics."
        ),
        AstraMasterclass.Unit(
            "ATP, cellular respiration and fermentation", "Intermediate",
            "Cells convert energy through coupled biochemical reactions rather than creating energy. Glycolysis takes place in the cytosol; pyruvate processing, the citric acid cycle and oxidative phosphorylation are interconnected in typical aerobic eukaryotic metabolism. ATP synthesis by oxidative phosphorylation depends on an electrochemical proton gradient across the inner mitochondrial membrane. Fermentation can regenerate NAD+ when respiration is constrained, allowing glycolysis to continue under suitable conditions. Actual ATP yield depends on organism and physiological conditions.",
            "For one glucose, glycolysis yields a net two ATP and produces pyruvate and reduced electron carriers in the conventional accounting. In lactic fermentation, reduction of pyruvate helps regenerate NAD+; the process does not make the same net ATP yield as complete aerobic oxidation.",
            "Trace carbon from glucose through glycolysis and the citric acid cycle, distinguishing carbon dioxide release from ATP production. Explain proton-motive force and draw a side-by-side flow diagram of aerobic respiration versus lactic fermentation.",
            "What is the main role of fermentation in maintaining glycolysis?",
            listOf("Producing oxygen from carbon dioxide", "Regenerating NAD+", "Replacing every enzyme with ATP"), 'B',
            "Fermentation helps restore oxidized NAD+ so glycolysis can continue when electron transfer through respiration is unavailable."
        ),
        AstraMasterclass.Unit(
            "Photosynthesis and the carbon cycle", "Intermediate",
            "Photosynthesis converts light energy into chemical potential and incorporates inorganic carbon into organic compounds. In oxygenic photosynthesis, the oxygen released originates from water splitting, while carbon fixation occurs through enzyme-driven reactions including the Calvin cycle. Light reactions create ATP and NADPH used in carbon fixation; an organism's net growth also depends on respiration, nutrients and environment. Chlorophyll absorption does not imply that all light wavelengths are equally useful, and photosynthesis is not a simple reversal of respiration at every step.",
            "A leaf under light may produce oxygen and consume CO2 while also continuing cellular respiration. If photosynthetic CO2 uptake exceeds respiratory CO2 release, net carbon fixation is positive. A leaf in darkness generally cannot sustain light-driven ATP and NADPH formation.",
            "Draw the movement of energy, water, O2, CO2, ATP and NADPH between light reactions and carbon fixation. Predict how water limitation, light intensity and temperature could change net photosynthesis, distinguishing each hypothesis from a measured result.",
            "What is the source of oxygen gas released in oxygenic photosynthesis?",
            listOf("Carbon dioxide", "Water", "Glucose"), 'B',
            "Photosystem II extracts electrons from water, releasing O2 as a byproduct."
        ),
        AstraMasterclass.Unit(
            "DNA replication and genomic integrity", "Intermediate",
            "DNA contains nucleotide sequences that can store hereditary information. Complementary pairing enables template-directed replication, with polymerases synthesizing new DNA in the 5′ to 3′ direction. Leading and lagging strands have different synthesis patterns because the templates run in opposite directions. Proofreading and repair reduce errors but do not make copying perfect; mutations may arise and affect function depending on sequence and context. DNA quantity alone is not a measure of organismal complexity.",
            "If a template DNA strand reads 3′-TACG-5′, the newly synthesized complementary strand is 5′-ATGC-3′. A polymerase extends the 3′ end of the nascent strand. A sequence substitution can be neutral, deleterious or beneficial depending on location, protein function and environment.",
            "Write complementary strands for three short DNA templates, label 5′ and 3′ ends and explain why lagging-strand replication involves multiple segments. Design a control to detect contamination in a hypothetical DNA amplification experiment.",
            "In which direction is new DNA synthesized by typical DNA polymerases?",
            listOf("3′ to 5′", "5′ to 3′", "Both directions at random"), 'B',
            "DNA polymerases add nucleotides to the growing strand's 3′ end, yielding 5′ to 3′ synthesis."
        ),
        AstraMasterclass.Unit(
            "From genes to proteins: transcription and translation", "Intermediate",
            "Gene expression commonly involves copying DNA information into RNA and translating messenger RNA into a polypeptide, but organisms also produce functional noncoding RNAs. During transcription, RNA polymerase uses a DNA template; during translation, ribosomes read mRNA codons with the aid of tRNA. Splicing and other processing steps modify many eukaryotic transcripts. The genetic code is redundant, so different codons can specify the same amino acid; genotype does not always determine phenotype without environment and regulation.",
            "An mRNA sequence 5′-AUG-GGC-UAA-3′ starts with AUG, which typically encodes methionine, followed by a glycine codon and then a stop codon. This tiny example illustrates initiation, coding and termination, not the full regulation of expression in a living cell.",
            "Transcribe a short template DNA sequence to RNA, partition the resulting mRNA into codons and use a code chart to translate. Explain the distinction between a gene, mRNA, ribosome, tRNA and a mature protein, and identify one role of RNA splicing.",
            "What molecular machine translates mRNA codons into a polypeptide?",
            listOf("Ribosome", "DNA polymerase", "Plasma membrane"), 'A',
            "Ribosomes coordinate tRNA decoding and peptide-bond formation during protein synthesis."
        ),
        AstraMasterclass.Unit(
            "Cell cycle, mitosis and meiosis", "Intermediate",
            "Most proliferating eukaryotic cells regulate DNA replication and chromosome separation through checkpoints and the cell cycle. Mitosis ordinarily produces daughter nuclei with matching chromosome sets, whereas meiosis includes homolog pairing, recombination and two divisions that can produce haploid cells. Crossing-over and independent assortment contribute to genetic variation. Chromosome number must be distinguished from DNA content and chromatid count; errors in segregation can cause aneuploidy. Not all cells in a multicellular organism continually divide.",
            "In a diploid organism with 2n=6, a somatic cell has six chromosomes in G1. After DNA replication it still has six replicated chromosomes but twelve chromatids. Following normal meiosis, resulting gametic nuclei typically have n=3 chromosomes.",
            "Draw simplified chromosome arrangements through G1, S phase, mitotic metaphase, meiotic prophase I and meiosis II for 2n=4. Label homologs versus sister chromatids and explain why meiosis reduces chromosome number.",
            "Which event creates genetic recombination between homologous chromosomes?",
            listOf("Crossing-over during meiosis I", "DNA replication alone", "Ordinary cytokinesis"), 'A',
            "Crossing-over between homologs during meiotic prophase I exchanges chromosome segments."
        ),
        AstraMasterclass.Unit(
            "Mendelian inheritance and probability", "Intermediate",
            "Mendelian models represent segregation of allele pairs during gamete formation and their combination at fertilization. A dominant allele can mask another allele's phenotype in a particular context, but 'dominant' does not mean common or advantageous. A monohybrid cross of two heterozygotes yields expected genotype proportions of 1:2:1 under standard segregation assumptions. Real inheritance can involve linkage, polygenic traits, incomplete dominance, penetrance and environmental effects. Probabilities describe expectations across many events, not guarantees for individual offspring.",
            "For Aa × Aa under a simple complete-dominance model, the Punnett square has AA, Aa, Aa and aa, giving probabilities 1/4 AA, 1/2 Aa and 1/4 aa. Dominant phenotype is expected in 3/4 of offspring, but a family of four need not have exactly three affected offspring.",
            "Construct two Punnett squares: Aa × Aa and Aa × aa. Compute genotype probabilities and distinguish phenotype predictions under complete dominance versus incomplete dominance. Explain why a genetic probability should not be presented as certainty.",
            "What proportion of AA genotypes is expected from Aa × Aa under simple Mendelian segregation?",
            listOf("One quarter", "One half", "Three quarters"), 'A',
            "Each parent contributes A with probability one half, so P(AA)=1/2×1/2=1/4."
        ),
        AstraMasterclass.Unit(
            "Evolution by natural selection and common ancestry", "Intermediate",
            "Evolution is change in heritable characteristics of populations over generations, supported by genetics, fossils, comparative anatomy and other evidence. Natural selection occurs when heritable variation produces differences in reproductive success under particular conditions. Mutation introduces genetic variation; selection is not a conscious effort by organisms to acquire needed traits. Common descent predicts nested relationships, although gene transfer and complex histories require care in microbes. Evolution does not imply a predetermined direction toward perfection.",
            "In a pesticide-exposed insect population, some individuals may already carry variants associated with resistance. If they survive and reproduce more often, their allele frequencies can rise over generations. Pesticide exposure does not necessarily create the exact adaptive mutation when needed.",
            "Describe the conditions needed for natural selection and trace a hypothetical resistant allele from low to high frequency. Distinguish mutation, genetic drift, migration and selection; identify what observational evidence could help tell them apart.",
            "Which change directly meets the population-genetic meaning of evolution?",
            listOf("An individual becoming stronger after exercise", "Heritable allele frequencies changing across generations", "A plant turning toward light during one day"), 'B',
            "Evolution occurs across generations in populations, not simply through an individual's acclimation."
        ),
        AstraMasterclass.Unit(
            "Population genetics, drift and speciation", "Advanced",
            "Allele frequencies reflect mutation, selection, migration, drift and nonrandom mating. The Hardy–Weinberg model predicts p², 2pq and q² genotype proportions when its assumptions hold; a mismatch can motivate investigation but is not itself proof of a particular cause. Genetic drift is random sampling of alleles and can be powerful in small populations. Speciation can evolve through reproductive isolation and diverging selection or drift; boundaries between species concepts vary among organisms. Correct inference needs genomic and ecological evidence.",
            "If a gene has two alleles A and a with p=0.7 and q=0.3, Hardy–Weinberg expected genotype proportions are AA 0.49, Aa 0.42 and aa 0.09. The arithmetic assumes random mating and other model conditions; observed values may differ owing to sampling and biological processes.",
            "Calculate genotype proportions for p=0.2, 0.5 and 0.9. Draw a small-population allele-drift simulation with random draws and compare its variability to a large population. Explain why founder effects can reduce diversity without intentional selection.",
            "Under Hardy–Weinberg conditions, what is the expected heterozygote frequency?",
            listOf("p²", "2pq", "q²"), 'B',
            "With two alleles of frequencies p and q, expected heterozygotes occur at frequency 2pq."
        ),
        AstraMasterclass.Unit(
            "Microbes, viruses and infectious transmission", "Intermediate",
            "Bacteria, archaea, fungi and protists differ in cellular organization and evolutionary history. Viruses depend on host systems for replication and are not bacteria; antibiotics that target bacterial pathways do not directly treat viral infections. Transmission involves pathogen source, route of exposure, host susceptibility and environment. Colonization, infection and disease are related but distinct conditions. Public-health interventions should consider evidence, sanitation, vaccination, and drug-resistance selection rather than claiming all microbes are harmful.",
            "An antibiotic targets bacterial peptidoglycan synthesis. It may inhibit susceptible bacteria but does not block viral replication by that mechanism. Repeated inappropriate antibiotic exposure can select resistant bacterial strains, while a separate antiviral strategy may target a virus-specific stage.",
            "Compare bacteria, an enveloped virus and yeast by genome, cellular structures, replication and intervention targets. Draw a transmission chain for a generic respiratory pathogen and identify ways to interrupt it without naming unsupported clinical treatments.",
            "Why do ordinary antibacterial antibiotics not directly treat viral replication?",
            listOf("Viruses are always larger than bacteria", "Viruses lack the targeted bacterial cellular machinery", "Viruses cannot evolve"), 'B',
            "Antibiotics target bacterial processes that viruses generally do not possess."
        ),
        AstraMasterclass.Unit(
            "Innate and adaptive immunity", "Intermediate",
            "Innate immunity includes physical barriers, inflammatory responses and cells that detect broadly conserved patterns. Adaptive immunity involves antigen-specific lymphocytes and immunological memory, with B cells able to produce antibodies and T cells participating in cellular responses. Vaccination may prime adaptive memory, but effectiveness and appropriate schedules depend on specific vaccines and populations. Inflammation is protective in some settings and damaging in others. A positive antibody test alone does not establish that every aspect of protective immunity is present.",
            "After a vaccine presents an antigen in an appropriate formulation, antigen-specific B and T cell responses can develop. Later exposure may trigger a faster memory response. This mechanism differs from giving ready-made antibodies, which provides passive protection without necessarily generating durable immune memory.",
            "Draw timelines for innate response, first adaptive response and subsequent memory response. Explain the roles of antigen presentation, B cells, T cells and antibodies and distinguish vaccination from passive antibody therapy.",
            "Which cells can produce antibodies after appropriate differentiation?",
            listOf("B-cell lineage plasma cells", "Red blood cells", "Platelets"), 'A',
            "Plasma cells differentiated from B cells secrete antibodies as part of humoral adaptive immunity."
        ),
        AstraMasterclass.Unit(
            "Nervous, endocrine and homeostatic control", "Intermediate",
            "Multicellular organisms coordinate internal processes through neural signaling, hormones and feedback circuits. Neurons propagate electrical signals through membrane potential changes and communicate at synapses using chemical or electrical mechanisms. Endocrine hormones travel through body fluids to targets with corresponding receptors. Negative feedback counteracts deviations from regulated conditions, while positive feedback can amplify a process in specific contexts. A symptom or single laboratory reading does not by itself identify a disease or justify medication changes.",
            "When blood glucose rises after a meal, a healthy body's insulin response contributes to glucose uptake and storage, helping reduce glucose toward a regulated range. The system uses multiple organs and signals; it is not a simple on/off thermostat or universal numerical rule.",
            "Draw a negative-feedback loop for thermoregulation or blood glucose, labeling sensor, controller, effector and response. Compare a fast synaptic signal with a hormone signal and explain how feedback differs from causation inferred from one correlation.",
            "What is the general effect of negative feedback in homeostasis?",
            listOf("It counteracts a deviation from a regulated state", "It always amplifies the initial disturbance", "It eliminates biological variation entirely"), 'A',
            "Negative feedback tends to reduce deviations, though real physiological systems have complex limits."
        ),
        AstraMasterclass.Unit(
            "Plant tissues, water transport and reproduction", "Intermediate",
            "Plants coordinate growth, carbon fixation, water transport and reproduction through differentiated tissues. Xylem transports water and dissolved minerals under mechanisms including tension and cohesion; phloem transports sugars and other solutes between sources and sinks. Stomata regulate gas exchange and water loss, creating tradeoffs under dry conditions. Flowering-plant reproduction involves pollen transfer, fertilization and seed development, but many plants reproduce by other life-cycle strategies. Root anatomy, hormones and environment modify growth responses.",
            "In a transpiring tree, evaporation from leaves can create tension in xylem water columns, supported by water cohesion and adhesion. Closing stomata reduces water loss but can also limit CO2 uptake and photosynthesis. A seed contains embryonic tissue and stored or accessible resources for early growth.",
            "Sketch root, stem and leaf cross-sections with xylem, phloem and stomata. Compare water and sugar movement; then plan a safe experiment testing how airflow influences transpiration with controls for leaf area and temperature.",
            "Which vascular tissue primarily conducts water and minerals from roots?",
            listOf("Xylem", "Phloem", "Epidermis alone"), 'A',
            "Xylem carries most long-distance water and mineral transport in vascular plants."
        ),
        AstraMasterclass.Unit(
            "Food webs, ecological energy and nutrient cycling", "Intermediate",
            "Ecosystems include organisms and abiotic surroundings linked by energy flows and matter cycling. Producers convert environmental energy into organic material; consumers and decomposers participate in transformations and transfers. Energy dissipates as heat through trophic transfers, whereas chemical elements such as carbon and nitrogen cycle among compartments. Ecological interactions include competition, predation, mutualism and parasitism. A food web is a simplified model, and an apparent species decline can have several interacting causes.",
            "Suppose producers fix 10,000 kJ of chemical energy over a period and primary consumers incorporate 1,000 kJ. The illustrative transfer efficiency is 10%, but actual efficiencies vary; 90% is not simply 'destroyed' energy, as respiration, waste and other flows distribute it.",
            "Draw a local grassland food web including plants, herbivores, predators and decomposers. Trace energy separately from nitrogen atoms and predict how reducing one consumer population could alter multiple species, noting uncertainty.",
            "Which statement about ecosystems is most accurate?",
            listOf("Energy is recycled without losses at every level", "Matter cycles while usable energy flows and dissipates", "Decomposers manufacture elements from nothing"), 'B',
            "Elements are transferred and recycled, while energy is transformed and dissipated through system processes."
        ),
        AstraMasterclass.Unit(
            "Population ecology, biodiversity and conservation", "Intermediate",
            "Population size changes through births, deaths, immigration and emigration. Exponential growth is a limiting model, while logistic growth adds density-dependent constraints and an idealized carrying capacity; neither predicts real populations perfectly. Biodiversity includes genetic, species and ecosystem diversity. Habitat fragmentation, pollution, overexploitation, introduced species and climate change can interact, and conservation interventions require baseline evidence and monitoring. Responsible biodiversity studies respect local rights and avoid harm to wildlife.",
            "A population begins at 100 individuals, with 15 births, 8 deaths, 4 immigrants and 3 emigrants during a period. The new size is 100+15−8+4−3=108. This arithmetic does not establish a long-term growth rate without the period length and other conditions.",
            "Build a simple population-accounting spreadsheet or table with births, deaths and migration. Describe how you would measure biodiversity in two habitats using consistent sampling and explain why comparing raw observed counts without effort adjustment may mislead.",
            "What is the correct population accounting equation?",
            listOf("Nnew=Nold+births−deaths+immigration−emigration", "Nnew=Nold+deaths−births", "Nnew=Nold−immigration only"), 'A',
            "Population accounting adds births and arrivals and subtracts deaths and departures."
        ),
        AstraMasterclass.Unit(
            "Biostatistics, research ethics and reproducibility", "Advanced",
            "Biological claims require attention to sampling, bias, uncertainty, hypothesis tests and practical effect sizes. Statistical significance alone does not establish that an effect is biologically important or causal. Study design must protect participants, animals and ecosystems, with appropriate consent, ethical review and data minimization. Laboratory and field notes should identify protocols, units, exclusions, missing observations and deviations. Small samples and multiple unplanned comparisons can produce misleading certainty even when arithmetic is correct.",
            "A study compares mean germination rates of 72% versus 75% across two groups of ten trays. Without the number of seeds per tray, between-tray variability, randomization and confidence intervals, the difference does not establish that one treatment reliably improves germination.",
            "Draft a preregistered outline for a noninvasive observational ecology project. Specify outcome, sampling frame, confounders, effect size, missing-data rules and consent or habitat-protection safeguards. Explain why negative results should still be documented.",
            "Which is the strongest inference from a low p-value alone?",
            listOf("The hypothesis is certainly true", "The treatment is clinically important", "The observations are comparatively unusual under the specified null model"), 'C',
            "A p-value describes compatibility with a specified statistical null, not effect importance or proof of causation."
        )
    )
}
