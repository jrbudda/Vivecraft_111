package com.mtbs3d.minecrift.render;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;

import org.apache.commons.io.IOUtils;

import net.minecraft.client.Minecraft;

public class VRShaders {
	private VRShaders() {
	}
	
	public static String load(String name){
		InputStreamReader in = new InputStreamReader(VRShaders.class.getResourceAsStream("/assets/vivecraft/shaders/" + name));
		String out = "";
		try {
			out =IOUtils.toString(in);
			in.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	return out;
	}
	
	public static final String DEPTH_MASK_VERTEX_SHADER = load("passthru.vsh");
	public static final String DEPTH_MASK_FRAGMENT_SHADER = load("mixedreality.fsh");
	public static final String LANCZOS_SAMPLER_VERTEX_SHADER= load("lanczos.vsh");
	public static final String LANCZOS_SAMPLER_FRAGMENT_SHADER= load("lanczos.fsh");
	public static final String FOV_REDUCTION_VERTEX_SHADER= load("passthru.vsh");
	public static final String FOV_REDUCTION_FRAGMENT_SHADER= load("fovreduction.vsh");
	
}
