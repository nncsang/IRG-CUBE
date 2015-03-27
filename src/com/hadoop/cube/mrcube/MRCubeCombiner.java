package com.hadoop.cube.mrcube;

import java.io.IOException;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.Reducer.Context;

import com.hadoop.cube.data_writable.Segment;

public class MRCubeCombiner extends Reducer<Segment,
									LongWritable,
									Segment,
									LongWritable> {

	public MRCubeCombiner() {
		//System.out.println("COMBINER");
	}
	
	private LongWritable SUM = new LongWritable(0);
	
	@Override
	protected void reduce(Segment key, Iterable<LongWritable> value, Context context)
	    throws IOException, InterruptedException {
		long sum = 0;
		for (LongWritable lw : value) {
		    sum += lw.get();
		}
		
		SUM.set(sum);
		context.write(key, SUM);
		//System.out.println(key);
	}
}
