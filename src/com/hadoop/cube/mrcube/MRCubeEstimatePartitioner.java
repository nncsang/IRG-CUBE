package com.hadoop.cube.mrcube;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Random;

import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Partitioner;

import com.hadoop.cube.settings.GlobalSettings;
import com.hadoop.cube.data_writable.Segment;
import com.hadoop.cube.data_writable.Tuple;

public class MRCubeEstimatePartitioner extends Partitioner<Segment, LongWritable> implements Configurable {
    
    protected MessageDigest m = null;
    protected int pivot = -1;
    protected int i;
    protected int choosen = 0;
    
    public MRCubeEstimatePartitioner() throws NoSuchAlgorithmException {
         m = MessageDigest.getInstance("MD5");
    }
    
    @Override
    public void setConf(Configuration conf) {
    	//System.out.println("PARTITION:");
    }

    @Override
    public Configuration getConf() {
        return null;
    }

    @Override
    public int getPartition(Segment segment, LongWritable value, int numReduceTasks) {
    	m.reset();
        m.update(ByteBuffer.allocate(4).putInt(segment.id).array());
        
        
        //System.out.println(segment + " " + (m.digest()[15] & Integer.MAX_VALUE) % numReduceTasks);
        return (m.digest()[15] & Integer.MAX_VALUE) % numReduceTasks;
    }
}