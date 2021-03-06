package com.hadoop.cube.mrcube;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import com.hadoop.cube.buc.BUC;
import com.hadoop.cube.data_structure.Batch;
import com.hadoop.cube.data_structure.CubeLattice;
import com.hadoop.cube.data_structure.Cuboid;
import com.hadoop.cube.data_structure.HeuristicBasedConverter;
import com.hadoop.cube.data_structure.RollUp;
import com.hadoop.cube.data_writable.Segment;
import com.hadoop.cube.data_writable.Tuple;
import com.hadoop.cube.settings.GlobalSettings;
import com.hadoop.cube.utils.Checker;
import com.hadoop.cube.utils.Utils;

public class MRCube extends Configured implements Tool{
	private int numReducers;
	private Path inputPath;
	private Path outputDir;
	private int tupleLength;
	private int reducerLimit;
	private int dataSize;
	public static void main(String args[]) throws Exception {
		int res = ToolRunner.run(new Configuration(), new MRCubeEstimate(args), args);
		res = ToolRunner.run(new Configuration(), new MRCubeIntermediate(args), args);
		res = ToolRunner.run(new Configuration(), new MRCube(args), args);
		System.exit(res);
	}
	
	public MRCube(String[] args) {
		if (args.length != 6) {
			System.out.println("Usage: MRCube <input_path> <output_path> <num_reducers> <tuple_length> <reducer_limit> <data_size>");
			System.exit(0);
		}
		
		this.inputPath = new Path(args[0]);
		this.outputDir = new Path(args[1]);
		this.numReducers = Integer.parseInt(args[2]);
		this.tupleLength = Integer.parseInt(args[3]);
		this.reducerLimit = Integer.parseInt(args[4]);
		this.dataSize = Integer.parseInt(args[5]);
		Tuple.setLength(tupleLength);
	}

	@Override
	public int run(String[] arg0) throws Exception {
	
		/** Final Aggregation Job **/
		Configuration conf = this.getConf();
		Job aggregating_job = new Job(conf, "FinalAggregation"); 
        
		FileSystem fs = FileSystem.get(conf);
		if(fs.exists(outputDir)){
			fs.delete(outputDir, true);
		}
		
        // set job input format
		aggregating_job.setInputFormatClass(SequenceFileInputFormat.class);

        // set map class and the map output key and value classes
		aggregating_job.setMapperClass(MRCubeAggregateMapper.class);
		aggregating_job.setMapOutputKeyClass(Tuple.class);
		aggregating_job.setMapOutputValueClass(LongWritable.class);
        
		aggregating_job.setPartitionerClass(MRCubeAggregatePartitioner.class);

        // set reduce class and the reduce output key and value classes
		aggregating_job.setCombinerClass(MRCubeAggregateCombiner.class);
		aggregating_job.setReducerClass(MRCubeAggregateReducer.class);
        
        //job.setSortComparatorClass(TimestampWritable.Comparator.class);

        // set job output format
		aggregating_job.setOutputKeyClass(Tuple.class);
		aggregating_job.setOutputValueClass(LongWritable.class);
		aggregating_job.setOutputFormatClass(TextOutputFormat.class);
        
        //job.setCombinerClass(ChainedCombiner.class);
        

        // add the input file as job input (from HDFS) to the variable
        // inputFile
        FileInputFormat.addInputPath(aggregating_job, new Path("output_mrcube_intermediate/part*"));

        // set the output path for the job results (to HDFS) to the
        // variable
        // outputPath
        FileOutputFormat.setOutputPath(aggregating_job, outputDir);

        // set the number of reducers using variable numberReducers
        aggregating_job.setNumReduceTasks(this.numReducers);

        // set the jar class
        aggregating_job.setJarByClass(MRCube.class);

        aggregating_job.waitForCompletion(true);
		Checker.main(null);
		return 0;
	}
}

class MRCubeEstimate extends Configured implements Tool{
	
	private int numReducers;
	private Path inputPath;
	private Path outputDir;
	private int tupleLength;
	private int reducerLimit;
	private int dataSize;
	
	public MRCubeEstimate(String[] args) {
		if (args.length != 6) {
			System.out.println("Usage: MRCube <input_path> <output_path> <num_reducers> <tuple_length> <reducer_limit> <data_size>");
			System.exit(0);
		}
		
		this.inputPath = new Path(args[0]);
		this.outputDir = new Path("output_mrcube_estimate");
		this.numReducers = Integer.parseInt(args[2]);
		this.tupleLength = Integer.parseInt(args[3]);
		this.reducerLimit = Integer.parseInt(args[4]);
		this.dataSize = Integer.parseInt(args[5]);
		Tuple.setLength(tupleLength);
	}
	
	@Override
	public int run(String[] arg0) throws Exception {
		Configuration conf = this.getConf();
		Job estimateJob = new Job(conf, "MRCubeEstimate"); 
		
		estimateJob.setInputFormatClass(SequenceFileInputFormat.class);
		
		estimateJob.setMapperClass(MRCubeEstimateMapper.class);
		estimateJob.setMapOutputKeyClass(Segment.class);
		estimateJob.setMapOutputValueClass(LongWritable.class);
		
		estimateJob.setPartitionerClass(MRCubeEstimatePartitioner.class);
		
		estimateJob.setReducerClass(MRCubeEstimateReducer.class);
		
		estimateJob.setOutputKeyClass(LongWritable.class);
		estimateJob.setOutputValueClass(Text.class);
		estimateJob.setOutputFormatClass(TextOutputFormat.class);
		
		estimateJob.setCombinerClass(MRCubeEstimateCombiner.class);
		
		FileInputFormat.addInputPath(estimateJob, inputPath);

		FileSystem fs = FileSystem.get(conf);
		
		if(fs.exists(outputDir)){
			fs.delete(outputDir, true);
		}
				
		FileOutputFormat.setOutputPath(estimateJob, outputDir);

		estimateJob.setNumReduceTasks(this.numReducers);
		
		String[] attributes = new String[this.tupleLength];
		
		for(int i = 0; i < this.tupleLength; i++)
			attributes[i] = Integer.toString(i);
		Tuple.setLength(tupleLength);
		
		CubeLattice cube = new CubeLattice(attributes);
		List<Cuboid> cuboids = cube.cuboids();
		String regionList = "";
		for(int i = 0; i < cuboids.size(); i++){
			String region = cuboids.get(i).toString();
			regionList = regionList + region + GlobalSettings.DELIM_BETWEEN_CONTENTS_OF_TUPLE;
		}
		
		regionList = regionList.substring(0, regionList.length() - 1);
		
		estimateJob.getConfiguration().set("attributes", Utils.join(attributes, GlobalSettings.DELIM_BETWEEN_ATTRIBUTES));
		estimateJob.getConfiguration().set("regionList", regionList);
		
		estimateJob.setJarByClass(MRCubeEstimate.class);
		estimateJob.waitForCompletion(true);
		
		return 0;
	}
}

class MRCubeIntermediate extends Configured implements Tool{
	private int numReducers;
	private Path inputPath;
	private Path outputDir;
	private int tupleLength;
	private int reducerLimit;
	private int dataSize;
	
	public MRCubeIntermediate(String[] args) {
		if (args.length != 6) {
			System.out.println("Usage: MRCube <input_path> <output_path> <num_reducers> <tuple_length> <reducer_limit> <data_size>");
			System.exit(0);
		}
		
		this.inputPath = new Path(args[0]);
		this.outputDir = new Path("output_mrcube_intermediate");
		this.numReducers = Integer.parseInt(args[2]);
		this.tupleLength = Integer.parseInt(args[3]);
		this.reducerLimit = Integer.parseInt(args[4]);
		this.dataSize = Integer.parseInt(args[5]);
		Tuple.setLength(tupleLength);
	}
	
	@Override
	public int run(String[] arg0) throws Exception {
		
		Configuration conf = this.getConf();
		Job job = new Job(conf, "MRCube"); 
		
		// set job input format
		job.setInputFormatClass(SequenceFileInputFormat.class);

		// set map class and the map output key and value classes
		job.setMapperClass(MRCubeMapper.class);
		job.setMapOutputKeyClass(Segment.class);
		job.setMapOutputValueClass(LongWritable.class);
		
		job.setPartitionerClass(MRCubePartitioner.class);
		//job.setSortComparatorClass(IRGPlusIRGSorter.class);
		
		// set reduce class and the reduce output key and value classes
		job.setReducerClass(MRCubeReducer.class);
		
		//job.setSortComparatorClass(TimestampWritable.Comparator.class);

		// set job output format
		job.setOutputKeyClass(Tuple.class);
		job.setOutputValueClass(LongWritable.class);
		job.setOutputFormatClass(SequenceFileOutputFormat.class);
		
		job.setCombinerClass(MRCubeCombiner.class);
		
		// add the input file as job input (from HDFS) to the variable
		// inputFile
		FileInputFormat.addInputPath(job, inputPath);

		// set the output path for the job results (to HDFS) to the
		// variable
		// outputPath
		//if file output is existed, delete it
		
		FileSystem fs = FileSystem.get(conf);
		
		if(fs.exists(outputDir)){
			fs.delete(outputDir, true);
		}
		
		//CubeLattice cube = GlobalSettings.cube;
		String[] attributes = new String[this.tupleLength];
		for(int i = 0; i < this.tupleLength; i++)
			attributes[i] = Integer.toString(i);
		Tuple.setLength(tupleLength);
		
		CubeLattice cube = new CubeLattice(attributes);
		List<Cuboid> cuboids = cube.cuboids();
		
		int nNeededTuple = (int)(100 * this.dataSize/ this.reducerLimit);
		GlobalSettings.RANDOM_RATE = (int) (nNeededTuple / (float) this.dataSize) * 100 + 5;
		int expectedSamplingSize = (int) (this.dataSize * GlobalSettings.RANDOM_RATE / 100.0);
		int realSamplingSize = 0;
		int reducerLimitForSampling = 0;
		
		try{
	        FileStatus[] status = fs.listStatus(new Path("output_mrcube_estimate"));
	 
	        for (int i = 0; i < status.length; i++){
	 
	            BufferedReader brIn=new BufferedReader(new InputStreamReader(fs.open(status[i].getPath())));
	            String line;
	            line=brIn.readLine();
	 
	            while (line != null){
	            	//System.out.println(line);
	            	String[] parts = line.split("\t");
	            	int id = Integer.parseInt(parts[0]);
	            	
	            	int maxTuple = Integer.parseInt(parts[1]);
	            	if (id == 0){
	            		realSamplingSize = maxTuple;
	            		reducerLimitForSampling = (int) (this.reducerLimit / (float)this.dataSize) * realSamplingSize;
	            	}
	            	
	            	if (maxTuple > reducerLimitForSampling){
	            		cuboids.get(id).setFriendly(false);
	            		cuboids.get(id).setPartitionFactor((int) (maxTuple / (float) reducerLimitForSampling) + 1);
	            	}
	                line=brIn.readLine();
	            }
	        }
	 
	    }catch(Exception e){
	        System.out.println(e.toString());
	    }
		
		/** for testing */
		if (cuboids.get(0).isFriendly == true){
			cuboids.get(0).setFriendly(false);
			cuboids.get(0).setPartitionFactor(2);
			
			cuboids.get(1).setFriendly(false);
			cuboids.get(1).setPartitionFactor(3);
		}
		
		cube.batching();
		
		MultipleOutputs.addNamedOutput(job, "tmp", SequenceFileOutputFormat.class, Tuple.class, LongWritable.class);
		FileOutputFormat.setOutputPath(job, outputDir);

		// set the number of reducers using variable numberReducers
		job.setNumReduceTasks(this.numReducers);

		// set the jar class
		job.setJarByClass(MRCubeIntermediate.class);
		
		String unfriendlyBatches = "";
		for(int i = 0; i < cube.unfriendlyBatches.size() - 1; i++)
			unfriendlyBatches += cube.unfriendlyBatches.get(i).convertToString() + "=";
		if (cube.unfriendlyBatches.size() >= 1)
			unfriendlyBatches += cube.unfriendlyBatches.get(cube.unfriendlyBatches.size() - 1).convertToString();
		
		List<BUC> bucs = new ArrayList<BUC>();
		
		List<List<Integer>> partitionOrder = new ArrayList<List<Integer>>();
		
		for(Batch batch: cube.friendlyBatches){
			/** CHECK THIS OUT for root is friendly**/
			BUC buc = new BUC(batch);
			//batch.print();
			//buc.print();
			//System.out.println(Utils.joinI(batch.cuboids.get(0).numPresentation, ""));
			bucs.add(buc);
			partitionOrder.add(batch.cuboids.get(0).numPresentation);
		}
		
		Segment.partitionOrder = partitionOrder;
		Segment.updateSortOrder();
		
		String bucsStr = "";
		for(int i = 0; i < bucs.size() - 1; i++){
			bucsStr += bucs.get(i).convertToString() + "z";
			//bucs.get(i).printSortSegments(bucs.get(i).sortSegments);
		}
		bucsStr += bucs.get(bucs.size() - 1).convertToString();
		
		
		job.getConfiguration().set("nBatch", Integer.toString(bucs.size()));
		job.getConfiguration().set("unfriendlyBatches", unfriendlyBatches);
		job.getConfiguration().set("bucsStr", bucsStr);
		
		job.waitForCompletion(true);
		
		
		return 0;
	}
}

