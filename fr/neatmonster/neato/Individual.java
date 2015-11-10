package fr.neatmonster.neato;

import static fr.neatmonster.neato.Population.BIAS_MUT;
import static fr.neatmonster.neato.Population.CONNECT_MUT;
import static fr.neatmonster.neato.Population.CONNECT_PERT;
import static fr.neatmonster.neato.Population.CONNECT_STEP;
import static fr.neatmonster.neato.Population.DISABLE_MUT;
import static fr.neatmonster.neato.Population.ENABLE_MUT;
import static fr.neatmonster.neato.Population.FITNESS;
import static fr.neatmonster.neato.Population.INPUTS;
import static fr.neatmonster.neato.Population.LINK_MUT;
import static fr.neatmonster.neato.Population.NODE_MUT;
import static fr.neatmonster.neato.Population.OUTPUTS;
import static fr.neatmonster.neato.Population.RANDOM;
import static fr.neatmonster.neato.Population.isInput;
import static fr.neatmonster.neato.Population.isOutput;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Individual {
    public final Set<Gene> genotype = new HashSet<Gene>();

    public final Set<Synapse> connects   = new HashSet<Synapse>();
    public final List<Neuron> inputs     = new ArrayList<Neuron>();
    public final List<Neuron> hidden     = new ArrayList<Neuron>();
    public final List<Neuron> outputs    = new ArrayList<Neuron>();
    public int                nextNeuron = INPUTS + OUTPUTS;

    public double[] fitness  = new double[FITNESS];
    public int      ranking  = 0;
    public double   distance = 0.0;

    public Individual() {
        mutate();
        generate();
    }

    public Individual(final Individual mother, final Individual father) {
        nextNeuron = Math.max(mother.nextNeuron, father.nextNeuron);
        looping: for (final Gene motherGene : mother.genotype) {
            for (final Gene fatherGene : father.genotype)
                if (motherGene.innovation == fatherGene.innovation)
                    if (RANDOM.nextBoolean()) {
                        genotype.add(fatherGene.clone());
                        continue looping;
                    } else
                        break;
            genotype.add(motherGene.clone());
        }
        generate();
    }

    public boolean dominates(final Individual other) {
        boolean dominates = false;
        for (int i = 0; i < fitness.length; ++i)
            if (fitness[i] < other.fitness[i])
                return false;
            else if (fitness[i] > other.fitness[i])
                dominates = true;
        return dominates;
    }

    public void feedForward() {
        final Set<Neuron> neurons = new HashSet<Neuron>();
        neurons.addAll(inputs);
        neurons.addAll(hidden);
        neurons.addAll(outputs);
        for (final Neuron neuron : neurons)
            neuron.updated = false;

        for (final Neuron input : inputs)
            input.feedForward();
    }

    public void generate() {
        final Map<Integer, Neuron> neurons = new HashMap<Integer, Neuron>();
        for (final Gene gene : genotype) {
            if (!neurons.containsKey(gene.input)) {
                final Neuron neuron = new Neuron();
                if (isInput(gene.input))
                    inputs.add(neuron);
                else if (isOutput(gene.input))
                    outputs.add(neuron);
                else
                    hidden.add(neuron);
                neurons.put(gene.input, neuron);
            }
            final Neuron input = neurons.get(gene.input);

            if (!neurons.containsKey(gene.output)) {
                final Neuron neuron = new Neuron();
                if (isInput(gene.output))
                    inputs.add(neuron);
                else if (isOutput(gene.output))
                    outputs.add(neuron);
                else
                    hidden.add(neuron);
                neurons.put(gene.output, neuron);
            }
            final Neuron output = neurons.get(gene.output);

            final Synapse connect = new Synapse();
            connect.input = input;
            connect.output = output;
            connect.weight = gene.weight;
            connect.enabled = gene.enabled;
            connect.innovation = gene.innovation;
            connects.add(connect);

            input.outputs.add(connect);
            output.inputs.add(connect);
        }
    }

    public double[] getOutput() {
        final double[] output = new double[OUTPUTS];
        for (int i = 0; i < output.length; ++i)
            output[i] = outputs.get(i).value;
        return output;
    }

    public void mutate() {
        if (RANDOM.nextDouble() < CONNECT_MUT)
            for (final Gene connect : genotype)
                if (RANDOM.nextDouble() < CONNECT_PERT) {
                    final double perturbation = 2.0 * CONNECT_STEP
                            * RANDOM.nextDouble() - CONNECT_STEP;
                    connect.weight = connect.weight + perturbation;
                } else
                    connect.weight = 2.0 * RANDOM.nextDouble() - 1.0;

        for (final Gene connect : genotype)
            if (connect.enabled && RANDOM.nextDouble() < DISABLE_MUT)
                connect.enabled = false;

        for (final Gene connect : genotype)
            if (!connect.enabled && RANDOM.nextDouble() < ENABLE_MUT)
                connect.enabled = true;

        if (RANDOM.nextDouble() < LINK_MUT) {
            int input, output;
            do {
                input = randomNeuron(true, true);
                output = randomNeuron(false, true);
            } while (input == output || isOutput(input) && isOutput(output));

            final Gene gene = new Gene();
            gene.input = input;
            gene.output = output;
            gene.innovation = ++Population.nextInnovation;
            genotype.add(gene);
        }

        if (RANDOM.nextDouble() < BIAS_MUT) {
            final int input = INPUTS - 1;
            int output;
            do
                output = randomNeuron(false, true);
            while (input == output);

            final Gene gene = new Gene();
            gene.input = input;
            gene.output = output;
            gene.innovation = ++Population.nextInnovation;
            genotype.add(gene);
        }

        if (RANDOM.nextDouble() < NODE_MUT) {
            final List<Gene> genes = new ArrayList<Gene>();
            for (final Gene gene : genotype)
                if (gene.enabled)
                    genes.add(gene);

            if (!genes.isEmpty()) {
                final Gene gene = genes.get(RANDOM.nextInt(genes.size()));
                gene.enabled = false;

                final Gene inputGene = new Gene();
                inputGene.input = gene.input;
                inputGene.output = nextNeuron;
                inputGene.weight = 1.0;
                inputGene.innovation = ++Population.nextInnovation;

                final Gene outputGene = new Gene();
                outputGene.input = nextNeuron;
                outputGene.output = gene.output;
                outputGene.innovation = ++Population.nextInnovation;

                ++nextNeuron;

                genotype.add(inputGene);
                genotype.add(outputGene);
            }
        }
    }

    private int randomNeuron(final boolean addInput, final boolean addOutput) {
        final List<Integer> neurons = new ArrayList<Integer>();

        if (addInput)
            for (int i = 0; i < INPUTS; ++i)
                neurons.add(i);

        if (addOutput)
            for (int i = 0; i < OUTPUTS; ++i)
                neurons.add(INPUTS + i);

        for (final Gene gene : genotype) {
            if ((addInput || !isInput(gene.input))
                    && (addOutput || !isOutput(gene.input)))
                neurons.add(gene.input);
            if ((addInput || !isInput(gene.output))
                    && (addOutput || !isOutput(gene.output)))
                neurons.add(gene.output);
        }

        return neurons.get(RANDOM.nextInt(neurons.size()));
    }

    public void setInput(final double[] input) {
        for (int i = 0; i < INPUTS; ++i)
            inputs.get(i).value = input[i];
    }
}
