package com.mtbs3d.minecrift.provider;

import com.mtbs3d.minecrift.api.*;
import com.mtbs3d.minecrift.control.VRControllerButtonMapping;
import com.mtbs3d.minecrift.control.ViveButtons;
import com.mtbs3d.minecrift.render.QuaternionHelper;
import com.mtbs3d.minecrift.settings.VRHotkeys;
import com.mtbs3d.minecrift.settings.VRSettings;
import com.mtbs3d.minecrift.utils.InputInjector;
import com.mtbs3d.minecrift.utils.KeyboardSimulator;
import com.mtbs3d.minecrift.utils.MCReflection;
import com.mtbs3d.minecrift.utils.Utils;
import com.mtbs3d.minecrift.utils.jkatvr;
import com.sun.jna.Memory;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;

import de.fruitfly.ovr.UserProfileData;
import de.fruitfly.ovr.structs.*;
import de.fruitfly.ovr.structs.Matrix4f;
import de.fruitfly.ovr.structs.Vector2f;
import de.fruitfly.ovr.structs.Vector3f;
import de.fruitfly.ovr.util.BufferUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Minecraft.renderPass;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiEnchantment;
import net.minecraft.client.gui.GuiHopper;
import net.minecraft.client.gui.GuiKeyBindingList;
import net.minecraft.client.gui.GuiRepair;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiWinGame;
import net.minecraft.client.gui.inventory.*;
import net.minecraft.client.main.Main;
import net.minecraft.client.renderer.GlStateManager.Color;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketClientStatus;
import net.minecraft.network.play.client.CPacketClientStatus.State;
import net.minecraft.src.Reflector;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.util.ByteArrayBuffer;
import org.lwjgl.LWJGLUtil;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.*;

import jopenvr.*;
import jopenvr.JOpenVRLibrary.EVREventType;

import java.awt.AWTException;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class MCOpenVR 
{
	static String initStatus;
	private static boolean initialized;
	private static Minecraft mc;

	public static VR_IVRSystem_FnTable vrsystem;
	static VR_IVRCompositor_FnTable vrCompositor;
	static VR_IVROverlay_FnTable vrOverlay;
	static VR_IVRSettings_FnTable vrSettings;
    static VR_IVRRenderModels_FnTable vrRenderModels;
	static VR_IVRChaperone_FnTable vrChaperone;
	 
	private static IntByReference hmdErrorStore = new IntByReference();
	private static IntBuffer hmdErrorStoreBuf;

	private static TrackedDevicePose_t.ByReference hmdTrackedDevicePoseReference;
	private static TrackedDevicePose_t[] hmdTrackedDevicePoses;
	private static TrackedDevicePose_t.ByReference hmdGamePoseReference;
	private static TrackedDevicePose_t[] hmdGamePoses;

	private static Matrix4f[] poseMatrices;
	private static Vec3d[] deviceVelocity;

	private LongByReference oHandle = new LongByReference();

	// position/orientation of headset and eye offsets
	private static final Matrix4f hmdPose = new Matrix4f();
	public static final Matrix4f hmdRotation = new Matrix4f();
	static Matrix4f hmdProjectionLeftEye;
	static Matrix4f hmdProjectionRightEye;
	static Matrix4f hmdPoseLeftEye = new Matrix4f();
	static Matrix4f hmdPoseRightEye = new Matrix4f();
	static boolean initSuccess = false, flipEyes = false;

	private static IntBuffer hmdDisplayFrequency;

	private static float vsyncToPhotons;
	private static double timePerFrame, frameCountRun;
	private static long frameCount;

	public static Vec3History hmdHistory = new Vec3History();
	public static Vec3History hmdPivotHistory = new Vec3History();
	public static Vec3History[] controllerHistory = new Vec3History[] { new Vec3History(), new Vec3History()};

	public static boolean isVive=true;

	// TextureIDs of framebuffers for each eye
	private int LeftEyeTextureId;

	final static VRTextureBounds_t texBounds = new VRTextureBounds_t();
	final static Texture_t texType0 = new Texture_t();
	final static Texture_t texType1 = new Texture_t();
	// aiming
	static float aimYaw = 0;
	static float aimPitch = 0;

	static float laimPitch = 0;
	static float laimYaw = 0;

	
	static float haimPitch = 0;
	static float haimYaw = 0;
	
	static Vec3d[] aimSource = new Vec3d[3];

	static Vector3f headDirection = new Vector3f();
	static Vector3f controllerDirection = new Vector3f();
	static Vector3f lcontrollerDirection = new Vector3f();
	static Vector3f thirdcontrollerDirection = new Vector3f();
	
	static Vector3f offset=new Vector3f(0,0,0);

	static boolean[] controllerTracking = new boolean[3];
	
	// Controllers
	private static int RIGHT_CONTROLLER = 0;
	private static int LEFT_CONTROLLER = 1;
	private static int THIRD_CONTROLLER = 2;
	private static Matrix4f[] controllerPose = new Matrix4f[3];
	private static Matrix4f[] controllerRotation = new Matrix4f[3];
	private static Matrix4f[] handRotation = new Matrix4f[3];
	private static int[] controllerDeviceIndex = new int[3];
	private static VRControllerState_t.ByReference[] inputStateRefernceArray = new VRControllerState_t.ByReference[3];
	private static VRControllerState_t[] lastControllerState = new VRControllerState_t[3];
	private static VRControllerState_t[] controllerStateReference = new VRControllerState_t[3];
	private static Matrix4f[] controllerTipTransform = new Matrix4f[3];
	
	// Vive axes
	private static int k_EAxis_Trigger = 1;
	private static int k_EAxis_TouchPad = 0;

	// Controls
	private static long k_buttonTouchpad = (1L << JOpenVRLibrary.EVRButtonId.EVRButtonId_k_EButton_SteamVR_Touchpad);
	private static long k_buttonTrigger = (1L << JOpenVRLibrary.EVRButtonId.EVRButtonId_k_EButton_SteamVR_Trigger);
	private static long k_buttonAppMenu = (1L << JOpenVRLibrary.EVRButtonId.EVRButtonId_k_EButton_ApplicationMenu);
	private static long k_buttonGrip =  (1L << JOpenVRLibrary.EVRButtonId.EVRButtonId_k_EButton_Grip);
	private static long k_button_A =  (1L << JOpenVRLibrary.EVRButtonId.EVRButtonId_k_EButton_A);
	private static long k_button_HandTrigger =  (1L << JOpenVRLibrary.EVRButtonId.EVRButtonId_k_EButton_Axis2);
	

	private static float triggerThreshold = .25f;

	public static Vector3f guiPos_Room = new Vector3f();
	public static Matrix4f guiRotationPose = new Matrix4f();
	public static  float guiScale = 1.0f;
	public static double startedOpeningInventory = 0;
	public static boolean hudPopup = true;
	
	// For mouse menu emulation
	private static float controllerMouseX = -1.0f;
	private static float controllerMouseY = -1.0f;
	public static boolean controllerMouseValid;
	public static int controllerMouseTicks;

	//keyboard
	public static boolean keyboardShowing = false;
	byte[] lastTyped = new byte[256];
	byte[] typed = new byte[256];
	static int pollsSinceLastChange = 0;

	// Touchpad samples
	private static Vector2f[][] touchpadSamples = new Vector2f[3][5];
	private static int[] touchpadSampleCount = new int[3];

	private static float[] inventory_swipeX = new float[3];
	private static float[] inventory_swipeY = new float[3];
	
	static boolean headIsTracking;
	
	private static int moveModeSwitchcount = 0;

	public static boolean isWalkingAbout;
	private static boolean isFreeRotate;
	private static float walkaboutYawStart;
	private static float hmdForwardYaw;
	
	public static boolean mrMovingCamActive;
	public static Vec3d mrControllerPos = Vec3d.ZERO;
	public static float mrControllerPitch;
	public static float mrControllerYaw;
	public static float mrControllerRoll;
	
	public static float rtbX, rtbY;
	
	public String getName() {
		return "OpenVR";
	}
	
	public String getID() {
		return "openvr";
	}

	static KeyBinding hotbarNext = new KeyBinding("Hotbar Next", 201, "Vivecraft");
	static KeyBinding hotbarPrev = new KeyBinding("Hotbar Prev", 209, "Vivecraft");
	static KeyBinding rotateLeft = new KeyBinding("Rotate Left", 203, "Vivecraft");
	static KeyBinding rotateRight = new KeyBinding("Rotate Right", 205, "Vivecraft");
	static KeyBinding walkabout = new KeyBinding("Walkabout", 207, "Vivecraft");
	static KeyBinding rotateFree = new KeyBinding("Rotate Free", 199, "Vivecraft");
	static KeyBinding quickTorch = new KeyBinding("Quick Torch", 210, "Vivecraft");
//	static KeyBinding scrollUp = new KeyBinding("Scroll Up", -1, "Vivecraft");
//	static KeyBinding scrollDown = new KeyBinding("Scroll Down", -1, "Vivecraft");
//someday.
	
	public MCOpenVR()
	{
		super();

		for (int c=0;c<3;c++)
		{
			aimSource[c] = new Vec3d(0.0D, 0.0D, 0.0D);
			for (int sample = 0; sample < 5; sample++)
			{
				touchpadSamples[c][sample] = new Vector2f(0, 0);
			}
			touchpadSampleCount[c] = 0;
			controllerPose[c] = new Matrix4f();
			controllerRotation[c] = new Matrix4f();
			handRotation[c] = new Matrix4f();
			controllerDeviceIndex[c] = -1;
			controllerTipTransform[c] = new Matrix4f();
			
			lastControllerState[c] = new VRControllerState_t();
			controllerStateReference[c] = new VRControllerState_t();
			inputStateRefernceArray[c] = new VRControllerState_t.ByReference();

			inputStateRefernceArray[c].setAutoRead(false);
			inputStateRefernceArray[c].setAutoWrite(false);
			inputStateRefernceArray[c].setAutoSynch(false);
			for (int i = 0; i < 5; i++)
			{
				lastControllerState[c].rAxis[i] = new VRControllerAxis_t();
			}


		}		
	}

	private static boolean tried;

	
	public static boolean init()  throws Exception
	{

		if ( initialized )
			return true;

		if ( tried )
			return initialized;


		tried = true;

		mc = Minecraft.getMinecraft();
		// look in .minecraft first for openvr_api.dll
		File minecraftDir = optifine.Utils.getWorkingDirectory(); // misleading name, actually the .minecraft directory
		File workingDir = new File(System.getProperty("user.dir"));
		
		String osname = System.getProperty("os.name").toLowerCase();
		String osarch= System.getProperty("os.arch").toLowerCase();

		String osFolder = "win32";
		
		if (osname.contains("windows")){	
			if (osarch.contains("64"))
			{
				osFolder = "win64";
			}
		}
		else if( osname.contains("linux")){
			osFolder = "linux32";
			if (osarch.contains("64"))
			{
				osFolder = "linux64";
			}
		}
		else if( osname.contains("mac")){
			osFolder = "osx32";
		}
		
		
		String openVRPath = new File(minecraftDir, osFolder).getPath();
		System.out.println("Adding OpenVR search path: " + openVRPath);
		NativeLibrary.addSearchPath("openvr_api", openVRPath);

		String openVRPath2 = new File(workingDir, osFolder).getPath();
		System.out.println("Adding OpenVR search path: " + openVRPath2);
		NativeLibrary.addSearchPath("openvr_api", openVRPath2);
			
		if(jopenvr.JOpenVRLibrary.VR_IsHmdPresent() == 0){
			initStatus =  "VR Headset not detected.";
			return false;
		}

		try {
			initializeJOpenVR();
			initOpenVRCompositor(true) ;
			initOpenVROverlay() ;	
			initOpenVRSettings();
			initOpenVRRenderModels();
			initOpenVRChaperone();
		} catch (Exception e) {
			e.printStackTrace();
			initSuccess = false;
			initStatus = e.getLocalizedMessage();
			return false;
		}

		System.out.println( "OpenVR initialized & VR connected." );

		deviceVelocity = new Vec3d[JOpenVRLibrary.k_unMaxTrackedDeviceCount];

		for(int i=0;i<poseMatrices.length;i++)
		{
			poseMatrices[i] = new Matrix4f();
			deviceVelocity[i] = new Vec3d(0,0,0);
		}

		HmdMatrix34_t matL = vrsystem.GetEyeToHeadTransform.apply(JOpenVRLibrary.EVREye.EVREye_Eye_Left);
		OpenVRUtil.convertSteamVRMatrix3ToMatrix4f(matL, hmdPoseLeftEye);

		HmdMatrix34_t matR = vrsystem.GetEyeToHeadTransform.apply(JOpenVRLibrary.EVREye.EVREye_Eye_Right);
		OpenVRUtil.convertSteamVRMatrix3ToMatrix4f(matR, hmdPoseRightEye);

	    mc.gameSettings.keyBindings = (KeyBinding[])((KeyBinding[])ArrayUtils.add(mc.gameSettings.keyBindings, rotateLeft));
	    mc.gameSettings.keyBindings = (KeyBinding[])((KeyBinding[])ArrayUtils.add(mc.gameSettings.keyBindings, rotateRight));
	    mc.gameSettings.keyBindings = (KeyBinding[])((KeyBinding[])ArrayUtils.add(mc.gameSettings.keyBindings, rotateFree));	
	    mc.gameSettings.keyBindings = (KeyBinding[])((KeyBinding[])ArrayUtils.add(mc.gameSettings.keyBindings, walkabout));	
	    mc.gameSettings.keyBindings = (KeyBinding[])((KeyBinding[])ArrayUtils.add(mc.gameSettings.keyBindings, quickTorch));	
	    mc.gameSettings.keyBindings = (KeyBinding[])((KeyBinding[])ArrayUtils.add(mc.gameSettings.keyBindings, hotbarNext));	
	    mc.gameSettings.keyBindings = (KeyBinding[])((KeyBinding[])ArrayUtils.add(mc.gameSettings.keyBindings, hotbarPrev));	
	
		
		initialized = true;
		
		if(Main.katvr){
			try {
				System.out.println( "Waiting for KATVR...." );
				NativeLibrary.addSearchPath(jkatvr.KATVR_LIBRARY_NAME, new File( minecraftDir, "katvr" ).getPath());		
				jkatvr.Init(1);
				jkatvr.Launch();
				if(jkatvr.CheckForLaunch()){
					System.out.println( "KATVR Loaded" );
				}else {
					System.out.println( "KATVR Failed to load" );
				}

			} catch (Exception e) {
				System.out.println( "KATVR crashed: " + e.getMessage() );
			}
		}
		
		return true;
	}

	final int rotationIncrement = 0;

	public static boolean isError(){
		return hmdErrorStore.getValue() != 0 || hmdErrorStoreBuf.get(0) != 0;
	}
	
	public static int getError(){
		return hmdErrorStore.getValue() != 0 ? hmdErrorStore.getValue() : hmdErrorStoreBuf.get(0);
	}
	
	private static void initializeJOpenVR() throws Exception { 
		hmdErrorStoreBuf = IntBuffer.allocate(1);
		vrsystem = null;
		JOpenVRLibrary.VR_InitInternal(hmdErrorStoreBuf, JOpenVRLibrary.EVRApplicationType.EVRApplicationType_VRApplication_Scene);
	
		if(!isError()) {
			// ok, try and get the vrsystem pointer..
			vrsystem = new VR_IVRSystem_FnTable(JOpenVRLibrary.VR_GetGenericInterface(JOpenVRLibrary.IVRSystem_Version, hmdErrorStoreBuf));
		}
		
		if( vrsystem == null || isError()) {
			throw new Exception(jopenvr.JOpenVRLibrary.VR_GetVRInitErrorAsEnglishDescription(getError()).getString(0));		
		} else {
			
			vrsystem.setAutoSynch(false);
			vrsystem.read();
			
			System.out.println("OpenVR initialized & VR connected.");
			
			hmdDisplayFrequency = IntBuffer.allocate(1);
			hmdDisplayFrequency.put( (int) JOpenVRLibrary.ETrackedDeviceProperty.ETrackedDeviceProperty_Prop_DisplayFrequency_Float);
			hmdTrackedDevicePoseReference = new TrackedDevicePose_t.ByReference();
			hmdTrackedDevicePoses = (TrackedDevicePose_t[])hmdTrackedDevicePoseReference.toArray(JOpenVRLibrary.k_unMaxTrackedDeviceCount);
			poseMatrices = new Matrix4f[JOpenVRLibrary.k_unMaxTrackedDeviceCount];
			for(int i=0;i<poseMatrices.length;i++) poseMatrices[i] = new Matrix4f();

			timePerFrame = 1.0 / hmdDisplayFrequency.get(0);

			// disable all this stuff which kills performance
			hmdTrackedDevicePoseReference.setAutoRead(false);
			hmdTrackedDevicePoseReference.setAutoWrite(false);
			hmdTrackedDevicePoseReference.setAutoSynch(false);
			for(int i=0;i<JOpenVRLibrary.k_unMaxTrackedDeviceCount;i++) {
				hmdTrackedDevicePoses[i].setAutoRead(false);
				hmdTrackedDevicePoses[i].setAutoWrite(false);
				hmdTrackedDevicePoses[i].setAutoSynch(false);
			}

			initSuccess = true;
		}
	}

	private static Pointer ptrFomrString(String in){
		Pointer p = new Memory(in.length()+1);
		p.setString(0, in);
		return p;

	}

	static void debugOut(){
		for(Field i :JOpenVRLibrary.ETrackedDeviceProperty.class.getDeclaredFields()){
			try {
				String[] ts = i.getName().split("_");
				String Type = ts[ts.length - 1];
				String out = "";

				
				if (Type.equals("Float")) {
					out += i.getName() + " " + vrsystem.GetFloatTrackedDeviceProperty.apply(JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd, i.getInt(null), hmdErrorStore);
				}				else if (Type.equals("String")) {
					Pointer pointer = new Memory(JOpenVRLibrary.k_unMaxPropertyStringSize);
					int len = vrsystem.GetStringTrackedDeviceProperty.apply(JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd, i.getInt(null), pointer, JOpenVRLibrary.k_unMaxPropertyStringSize - 1, hmdErrorStore);
					out += i.getName() + " " + pointer.getString(0);
				} else if (Type.equals("Bool")) {
					out += i.getName() + " " + vrsystem.GetBoolTrackedDeviceProperty.apply(JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd, i.getInt(null), hmdErrorStore);
				} else if (Type.equals("Int32")) {
					out += i.getName() + " " + vrsystem.GetInt32TrackedDeviceProperty.apply(JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd, i.getInt(null), hmdErrorStore);
				} else if (Type.equals("Uint64")) {
					out += i.getName() + " " + vrsystem.GetUint64TrackedDeviceProperty.apply(JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd, i.getInt(null), hmdErrorStore);
				}
				System.out.println(out);
			}catch (IllegalAccessException e){
				e.printStackTrace();
			}
		}

		System.out.println("TrackingSpace: "+vrCompositor.GetTrackingSpace.apply());
	}


	// needed for in-game keyboard
	public static void initOpenVROverlay() throws Exception
	{
		vrOverlay =   new VR_IVROverlay_FnTable(JOpenVRLibrary.VR_GetGenericInterface(JOpenVRLibrary.IVROverlay_Version, hmdErrorStoreBuf));
		if (vrOverlay != null &&  !isError()) {     		
			vrOverlay.setAutoSynch(false);
			vrOverlay.read();					
			System.out.println("OpenVR Overlay initialized OK");
		} else {
			if (getError() != 0) {
				String str = jopenvr.JOpenVRLibrary.VR_GetVRInitErrorAsEnglishDescription(getError()).getString(0);
				System.out.println("VROverlay init failed: " + str);
				vrOverlay = null;
			} else {
				throw new Exception(jopenvr.JOpenVRLibrary.VR_GetVRInitErrorAsEnglishDescription(getError()).getString(0));
			}	
		}
	}


	public static void initOpenVRSettings() throws Exception
	{
		vrSettings = new VR_IVRSettings_FnTable(JOpenVRLibrary.VR_GetGenericInterface(JOpenVRLibrary.IVRSettings_Version, hmdErrorStoreBuf));
		if (vrSettings != null &&  !isError()) {     		
			vrSettings.setAutoSynch(false);
			vrSettings.read();					
			System.out.println("OpenVR Settings initialized OK");
		} else {
			if (getError() != 0) {
				System.out.println("VRSettings init failed: " + jopenvr.JOpenVRLibrary.VR_GetVRInitErrorAsEnglishDescription(getError()).getString(0));
				vrSettings = null;
			} else {
				throw new Exception(jopenvr.JOpenVRLibrary.VR_GetVRInitErrorAsEnglishDescription(getError()).getString(0));
			}	
		}
	}
	
	
		public static void initOpenVRRenderModels() throws Exception
		{
			vrRenderModels = new VR_IVRRenderModels_FnTable(JOpenVRLibrary.VR_GetGenericInterface(JOpenVRLibrary.IVRRenderModels_Version, hmdErrorStoreBuf));
			if (vrRenderModels != null && !isError()) {
				vrRenderModels.setAutoSynch(false);
				vrRenderModels.read();			
				System.out.println("OpenVR RenderModels initialized OK");
			} else {
				if (getError() != 0) {
					System.out.println("VRRenderModels init failed: " + jopenvr.JOpenVRLibrary.VR_GetVRInitErrorAsEnglishDescription(getError()).getString(0));
					vrRenderModels = null;
				} else {
					throw new Exception(jopenvr.JOpenVRLibrary.VR_GetVRInitErrorAsEnglishDescription(getError()).getString(0));
				}
			}
		}
	
		private static void initOpenVRChaperone() throws Exception {
			vrChaperone = new VR_IVRChaperone_FnTable(JOpenVRLibrary.VR_GetGenericInterface(JOpenVRLibrary.IVRChaperone_Version, hmdErrorStoreBuf));
			if (vrChaperone != null && hmdErrorStore.getValue() == 0) {
				vrChaperone.setAutoSynch(false);
				vrChaperone.read();
				System.out.println("OpenVR chaperone initialized.");
			} else {
				if (getError() != 0) {
					System.out.println("VRChaperone init failed: " + jopenvr.JOpenVRLibrary.VR_GetVRInitErrorAsEnglishDescription(getError()).getString(0));
					vrChaperone = null;
				} else {
					throw new Exception(jopenvr.JOpenVRLibrary.VR_GetVRInitErrorAsEnglishDescription(getError()).getString(0));
				}
			}
		}
		
		private static void getTipTransforms(){
			if (vrRenderModels == null) return;
			int count = vrRenderModels.GetRenderModelCount.apply();
			Pointer pointer = new Memory(JOpenVRLibrary.k_unMaxPropertyStringSize);
			for (int i = 0; i < 2; i++) {
				if (controllerDeviceIndex[i] != -1 && !mc.vrSettings.seated) {
					vrsystem.GetStringTrackedDeviceProperty.apply(controllerDeviceIndex[i], JOpenVRLibrary.ETrackedDeviceProperty.ETrackedDeviceProperty_Prop_RenderModelName_String, pointer, JOpenVRLibrary.k_unMaxPropertyStringSize - 1, hmdErrorStore);
					RenderModel_ControllerMode_State_t modeState = new RenderModel_ControllerMode_State_t();
					RenderModel_ComponentState_t componentState = new RenderModel_ComponentState_t();
					vrRenderModels.GetComponentState.apply(pointer, ptrFomrString("tip"), controllerStateReference[i], modeState, componentState);
					OpenVRUtil.convertSteamVRMatrix3ToMatrix4f(componentState.mTrackingToComponentLocal, controllerTipTransform[i]);
				} else {
					OpenVRUtil.Matrix4fSetIdentity(controllerTipTransform[i]);
				}
			}
		}
		
	
	public static void initOpenVRCompositor(boolean set) throws Exception
	{
		if( set && vrsystem != null ) {
			vrCompositor = new VR_IVRCompositor_FnTable(JOpenVRLibrary.VR_GetGenericInterface(JOpenVRLibrary.IVRCompositor_Version, hmdErrorStoreBuf));
			if(vrCompositor != null && !isError()){                
				System.out.println("OpenVR Compositor initialized OK.");
				vrCompositor.setAutoSynch(false);
				vrCompositor.read();
				vrCompositor.SetTrackingSpace.apply(JOpenVRLibrary.ETrackingUniverseOrigin.ETrackingUniverseOrigin_TrackingUniverseStanding);

				int buffsize=20;
				Pointer s=new Memory(buffsize);

				//vrCompositor.GetTrackingSpace.apply();
				debugOut();

				vrsystem.GetStringTrackedDeviceProperty.apply(JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd,JOpenVRLibrary.ETrackedDeviceProperty.ETrackedDeviceProperty_Prop_ManufacturerName_String,s,buffsize,hmdErrorStore);
				String id=s.getString(0);
				System.out.println("Device manufacturer is: "+id);
			
				if(!id.equals("HTC")) {
					isVive=false;
					mc.vrSettings.loadOptions();
				}
				
				//TODO: detect tracking system
				if(mc.vrSettings.seated && !isVive)
					resetPosition();
				else
					clearOffset();
				
			} else {
				throw new Exception(jopenvr.JOpenVRLibrary.VR_GetVRInitErrorAsEnglishDescription(getError()).getString(0));			 
			}
		}
		if( vrCompositor == null ) {
			System.out.println("Skipping VR Compositor...");
			if( vrsystem != null ) {
				vsyncToPhotons = vrsystem.GetFloatTrackedDeviceProperty.apply(JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd, JOpenVRLibrary.ETrackedDeviceProperty.ETrackedDeviceProperty_Prop_SecondsFromVsyncToPhotons_Float, hmdErrorStore);


			} else {
				vsyncToPhotons = 0f;
			}
		}

		// left eye
		texBounds.uMax = 1f;
		texBounds.uMin = 0f;
		texBounds.vMax = 1f;
		texBounds.vMin = 0f;
		texBounds.setAutoSynch(false);
		texBounds.setAutoRead(false);
		texBounds.setAutoWrite(false);
		texBounds.write();


		// texture type
		texType0.eColorSpace = JOpenVRLibrary.EColorSpace.EColorSpace_ColorSpace_Gamma;
		texType0.eType = JOpenVRLibrary.ETextureType.ETextureType_TextureType_OpenGL;
		texType0.setAutoSynch(false);
		texType0.setAutoRead(false);
		texType0.setAutoWrite(false);
		texType0.handle = Pointer.createConstant(-1);
		texType0.write();

		
		// texture type
		texType1.eColorSpace = JOpenVRLibrary.EColorSpace.EColorSpace_ColorSpace_Gamma;
		texType1.eType = JOpenVRLibrary.ETextureType.ETextureType_TextureType_OpenGL;
		texType1.setAutoSynch(false);
		texType1.setAutoRead(false);
		texType1.setAutoWrite(false);
		texType1.handle = Pointer.createConstant(-1);
		texType1.write();
		
		System.out.println("OpenVR Compositor initialized OK.");

	}

	public boolean initOpenVRControlPanel()
	{
		return true;
		//		vrControlPanel = new VR_IVRSettings_FnTable(JOpenVRLibrary.VR_GetGenericInterface(JOpenVRLibrary.IVRControlPanel_Version, hmdErrorStore));
		//		if(vrControlPanel != null && hmdErrorStore.getValue() == 0){
		//			System.out.println("OpenVR Control Panel initialized OK.");
		//			return true;
		//		} else {
		//			initStatus = "OpenVR Control Panel error: " + JOpenVRLibrary.VR_GetStringForHmdError(hmdErrorStore.getValue()).getString(0);
		//			return false;
		//		}
	}

	private String lasttyped = "";

	
	public static void poll(long frameIndex)
	{
		Minecraft.getMinecraft().mcProfiler.startSection("input");
		boolean sleeping = (mc.world !=null && mc.player != null && mc.player.isPlayerSleeping());		
			
		pollInputEvents();

		if(!mc.vrSettings.seated){

			updateControllerButtonState();
			updateTouchpadSampleBuffer();

			if(mc.world != null){
				if(MCOpenVR.isVive){
					processControllerButtons(sleeping, mc.currentScreen != null);
					processTouchpadSampleBuffer();
				}else {
					processControllerButtonsOculus(sleeping, mc.currentScreen != null);
				}
			}

			// GUI controls

			Minecraft.getMinecraft().mcProfiler.endStartSection("gui");
			if(mc.currentScreen != null)
			{
				processGui();
			}

			if(mc.currentScreen == null && mc.vrSettings.vrTouchHotbar && mc.vrSettings.vrHudLockMode != mc.vrSettings.HUD_LOCK_HEAD && hudPopup){
				processHotbar();
			}
		}

		processVRFunctions(sleeping, mc.currentScreen != null);

		Minecraft.getMinecraft().mcProfiler.endStartSection("updatePose");
			updatePose();
		Minecraft.getMinecraft().mcProfiler.endSection();
	}

	static GuiTextField keyboardGui;
	private static int quickTorchPreviousSlot;

	public static boolean setKeyboardOverlayShowing(boolean showingState, GuiTextField gui) {
		if (vrOverlay == null) return false;
		if(mc.vrSettings.seated) showingState = false;
		keyboardGui = gui;
		int ret = 1;
		if (showingState) {
			pollsSinceLastChange = 0; // User deliberately tried to show keyboard, shouldn't have chance of immediately resetting   
			Pointer pointer = new Memory(3);
			pointer.setString(0, "mc");
			Pointer empty = new Memory(1);
			empty.setString(0, "");

			ret = vrOverlay.ShowKeyboard.apply(0, 0, pointer, 256, empty, (byte)1, 0);
			keyboardShowing = 0 == ret; //0 = no error, > 0 see EVROverlayError
	
			if (ret != 0) {
				String err = vrOverlay.GetOverlayErrorNameFromEnum.apply(ret).getString(0);
				System.out.println("VR Overlay Error: " + err);
				if(err.equalsIgnoreCase("VROverlayError_KeyboardAlreadyInUse")) keyboardShowing = true;
			}

			if (mc.currentScreen != null) orientKeyboardOverlay(true);
		} else {
			try {
					vrOverlay.HideKeyboard.apply();				
			} catch (Error e) {
				// TODO: handle exception
			}
			keyboardShowing = false;
		}

		return keyboardShowing;
	}

	public static void orientKeyboardOverlay(boolean guiRelative) {
		if (vrOverlay == null) return;
		if (!keyboardShowing) return;
		org.lwjgl.util.vector.Matrix4f matrix = new org.lwjgl.util.vector.Matrix4f();
		if (guiRelative) {
			org.lwjgl.util.vector.Matrix4f guiRot = Utils.convertOVRMatrix(guiRotationPose);
			Vec3d guiUp = new Vec3d(guiRot.m10, guiRot.m11, guiRot.m12);
			guiUp = guiUp.scale(guiScale);
			matrix.rotate((float)Math.toRadians(mc.vrSettings.vrWorldRotation), new org.lwjgl.util.vector.Vector3f(0, -1, 0)); // negate world rotation
			matrix.translate(new org.lwjgl.util.vector.Vector3f(guiPos_Room.x - (float)guiUp.xCoord, guiPos_Room.y - (float)guiUp.yCoord, guiPos_Room.z - (float)guiUp.zCoord));
			org.lwjgl.util.vector.Matrix4f.mul(matrix, guiRot, matrix);
			matrix.rotate((float)Math.toRadians(30), new org.lwjgl.util.vector.Vector3f(-1, 0, 0)); // tilt it a bit
		} else {
			Vec3d hmdPos = mc.roomScale.getHMDPos_Room();
			Vec3d hmdDir = mc.vrPlayer.getHMDDir_Room();
			hmdDir = hmdDir.scale(mc.vrSettings.hudDistance);
			matrix.translate(new org.lwjgl.util.vector.Vector3f((float)hmdPos.xCoord + (float)hmdDir.xCoord, (float)hmdPos.yCoord - 1.0F, (float)hmdPos.zCoord + (float)hmdDir.zCoord));
			matrix.rotate((float)Math.toRadians(mc.vrPlayer.getHMDYaw_Room() + 180), new org.lwjgl.util.vector.Vector3f(0, -1, 0)); // +180 because it needs to face towards the HMD
			matrix.rotate((float)Math.toRadians(30), new org.lwjgl.util.vector.Vector3f(-1, 0, 0)); // tilt it a bit
		}
		vrOverlay.SetKeyboardTransformAbsolute.apply(JOpenVRLibrary.ETrackingUniverseOrigin.ETrackingUniverseOrigin_TrackingUniverseStanding, Utils.convertToMatrix34(matrix));
	}
	
	private static Vec3d vecFromVector(Vector3f in){
		return new Vec3d(in.x, in.y, in.z);
	}
	private static void processHotbar() {
		
		if(mc.player == null) return;
		if(mc.player.inventory == null) return;
		if(mc.climbTracker.isGrabbingLadder()) return;
		
		Vec3d main = getAimSource(0);
		Vec3d off = getAimSource(1);
		
		Vec3d barStartos = null,barEndos = null;
		
		int i = 1;
		if(mc.vrSettings.vrReverseHands) i = -1;
		
		if (mc.vrSettings.vrHudLockMode == mc.vrSettings.HUD_LOCK_WRIST){
			 barStartos = vecFromVector( getAimRotation(1).transform(new Vector3f(i*0.06f,-0.05f,0.24f)));
			 barEndos = vecFromVector( getAimRotation(1).transform(new Vector3f(i*0.22f,-0.05f,-0.05f)));
		} else if (mc.vrSettings.vrHudLockMode == mc.vrSettings.HUD_LOCK_HAND){
			 barStartos = vecFromVector( getAimRotation(1).transform(new Vector3f(i*-.18f,0.08f,-0.01f)));
			 barEndos = vecFromVector( getAimRotation(1).transform(new Vector3f(i*0.19f,0.04f,-0.08f)));
		} else return; //how did u get here
		
	
		Vec3d barStart = off.addVector(barStartos.xCoord, barStartos.yCoord, barStartos.zCoord);	
		Vec3d barEnd = off.addVector(barEndos.xCoord, barEndos.yCoord, barEndos.zCoord);

		Vec3d u = barStart.subtract(barEnd);
		Vec3d pq = barStart.subtract(main);
		float dist = (float) (pq.crossProduct(u).lengthVector() / u.lengthVector());

		if(dist > 0.06) return;
		
		float fact = (float) (pq.dotProduct(u) / (u.xCoord*u.xCoord + u.yCoord*u.yCoord + u.zCoord*u.zCoord));
	
		if(fact < 0) return;
		
		Vec3d w2 = u.scale(fact).subtract(pq);
	
		Vec3d point = main.subtract(w2);
		float linelen = (float) barStart.subtract(barEnd).lengthVector();
		float ilen = (float) barStart.subtract(point).lengthVector();

		float pos = ilen / linelen * 9; 
		
		if(mc.vrSettings.vrReverseHands) pos = 9 - pos;
		
		int box = (int) Math.floor(pos);
		if(pos - Math.floor(pos) < 0.1) return;
		
		if(box > 8) return;
		if(box < 0) return;
		//all that maths for this.
		if(box != mc.player.inventory.currentItem){
		mc.player.inventory.currentItem = box;	
		triggerHapticPulse(0, 750);
		}
	}
	
	
	//TODO: to hell with all these conversions.
	//sets mouse position for currentscreen
	private static void processGui() {

		if(guiRotationPose == null) return;

		Vector3f controllerPos = new Vector3f();
		//OpenVRUtil.convertMatrix4ftoTranslationVector(controllerPose[0]);
		Vec3d con = mc.entityRenderer.getControllerRenderPos(0);
		controllerPos.x	= (float) con.xCoord;
		controllerPos.y	= (float) con.yCoord;
		controllerPos.z	= (float) con.zCoord;

		Vec3d controllerdir = mc.roomScale.getControllerDir_World(0);
		Vector3f cdir = new Vector3f((float)controllerdir.xCoord,(float) controllerdir.yCoord,(float) controllerdir.zCoord);
		Vector3f forward = new Vector3f(0,0,1);

		Vector3f guiNormal = guiRotationPose.transform(forward);
		Vector3f guiRight = guiRotationPose.transform(new Vector3f(1,0,0));
		Vector3f guiUp = guiRotationPose.transform(new Vector3f(0,1,0));

		float guiWidth = 1.0f;		
		float guiHalfWidth = guiWidth * 0.5f;		
		float guiHeight = 1.0f;	
		float guiHalfHeight = guiHeight * 0.5f;

		Vector3f gp = new Vector3f();

		gp.x = (float) (guiPos_Room.x + mc.entityRenderer.interPolatedRoomOrigin.xCoord ) ;
		gp.y = (float) (guiPos_Room.y + mc.entityRenderer.interPolatedRoomOrigin.yCoord ) ;
		gp.z = (float) (guiPos_Room.z + mc.entityRenderer.interPolatedRoomOrigin.zCoord ) ;

		Vector3f guiTopLeft = gp.subtract(guiUp.divide(1.0f / guiHalfHeight)).subtract(guiRight.divide(1.0f/guiHalfWidth));
		Vector3f guiTopRight = gp.subtract(guiUp.divide(1.0f / guiHalfHeight)).add(guiRight.divide(1.0f / guiHalfWidth));

		//Vector3f guiBottomLeft = guiPos.add(guiUp.divide(1.0f / guiHalfHeight)).subtract(guiRight.divide(1.0f/guiHalfWidth));
		//Vector3f guiBottomRight = guiPos.add(guiUp.divide(1.0f / guiHalfHeight)).add(guiRight.divide(1.0f/guiHalfWidth));

		float guiNormalDotControllerDirection = guiNormal.dot(cdir);
		if (Math.abs(guiNormalDotControllerDirection) > 0.00001f)
		{//pointed normal to the GUI
			float intersectDist = -guiNormal.dot(controllerPos.subtract(guiTopLeft)) / guiNormalDotControllerDirection;
			Vector3f pointOnPlane = controllerPos.add(cdir.divide(1.0f/intersectDist));

			Vector3f relativePoint = pointOnPlane.subtract(guiTopLeft);
			float u = relativePoint.dot(guiRight.divide(1.0f/guiWidth));
			float v = relativePoint.dot(guiUp.divide(1.0f/guiWidth));


			// adjust vertical for aspect ratio
			v = ( (v - 0.5f) * ((float)mc.displayWidth / (float)mc.displayHeight) ) + 0.5f;

			// TODO: Figure out where this magic 0.68f comes from. Probably related to Minecraft window size.
			//JRBUDDA: It's probbably 1/defaulthudscale (1.5)

			u = ( u - 0.5f ) * 0.68f / guiScale + 0.5f;
			v = ( v - 0.5f ) * 0.68f / guiScale + 0.5f;

			if (u<0 || v<0 || u>1 || v>1)
			{
				// offscreen
				controllerMouseX = -1.0f;
				controllerMouseY = -1.0f;
			}
			else if (controllerMouseX == -1.0f)
			{
				controllerMouseX = (int) (u * mc.displayWidth);
				controllerMouseY = (int) (v * mc.displayHeight);
			}
			else
			{
				// apply some smoothing between mouse positions
				float newX = (int) (u * mc.displayWidth);
				float newY = (int) (v * mc.displayHeight);
				controllerMouseX = controllerMouseX * 0.7f + newX * 0.3f;
				controllerMouseY = controllerMouseY * 0.7f + newY * 0.3f;
			}

			// copy to mc for debugging
			mc.guiU = u;
			mc.guiV = v;
			mc.intersectDist = intersectDist;
			mc.pointOnPlaneX = pointOnPlane.x;
			mc.pointOnPlaneY = pointOnPlane.y;
			mc.pointOnPlaneZ = pointOnPlane.z;
			mc.guiTopLeftX = guiTopLeft.x;
			mc.guiTopLeftY = guiTopLeft.y;
			mc.guiTopLeftZ = guiTopLeft.z;
			mc.guiTopRightX = guiTopRight.x;
			mc.guiTopRightY = guiTopRight.y;
			mc.guiTopRightZ = guiTopRight.z;
			mc.controllerPosX = controllerPos.x;
			mc.controllerPosY = controllerPos.y;
			mc.controllerPosZ = controllerPos.z;
		}

		boolean lastpressedShift,pressedshift,lastpressedleftclick,
		lastpressedrightclick,lastpressedmiddleclick,pressedleftclick,pressedrightclick,pressedmiddleclick;

		if(MCOpenVR.isVive){
			//left controller
			lastpressedShift = (lastControllerState[LEFT_CONTROLLER].ulButtonPressed & k_buttonGrip) > 0;			
			pressedshift = (controllerStateReference[LEFT_CONTROLLER].ulButtonPressed & k_buttonGrip) > 0;
			//right controller
			lastpressedleftclick = lastControllerState[RIGHT_CONTROLLER].rAxis[k_EAxis_Trigger].x > triggerThreshold;
			lastpressedrightclick = (lastControllerState[RIGHT_CONTROLLER].ulButtonPressed & k_buttonTouchpad) > 0 ;
			lastpressedmiddleclick= (lastControllerState[RIGHT_CONTROLLER].ulButtonPressed & k_buttonGrip) > 0 ;
			pressedleftclick = controllerStateReference[RIGHT_CONTROLLER].rAxis[k_EAxis_Trigger].x > triggerThreshold;
			pressedrightclick = (controllerStateReference[RIGHT_CONTROLLER].ulButtonPressed & k_buttonTouchpad) > 0;
			pressedmiddleclick = (controllerStateReference[RIGHT_CONTROLLER].ulButtonPressed & k_buttonGrip) > 0;
		} else {
			//left controller
			lastpressedShift = (lastControllerState[LEFT_CONTROLLER].ulButtonPressed & k_button_HandTrigger) > 0;			
			pressedshift = (controllerStateReference[LEFT_CONTROLLER].ulButtonPressed & k_button_HandTrigger) > 0;
			//right controller
			lastpressedleftclick = (lastControllerState[RIGHT_CONTROLLER].ulButtonPressed & k_buttonTrigger) > 0;		
			lastpressedmiddleclick = (lastControllerState[RIGHT_CONTROLLER].ulButtonPressed & k_buttonGrip) > 0 ;		
			lastpressedrightclick= (lastControllerState[RIGHT_CONTROLLER].ulButtonPressed & k_button_A) > 0 ;
			
			pressedleftclick = (controllerStateReference[RIGHT_CONTROLLER].ulButtonPressed & k_buttonTrigger) > 0;
			pressedmiddleclick = (controllerStateReference[RIGHT_CONTROLLER].ulButtonPressed & k_buttonGrip) > 0;	
			pressedrightclick = (controllerStateReference[RIGHT_CONTROLLER].ulButtonPressed & k_button_A) > 0;	
			
			
		}

		if (controllerDeviceIndex[LEFT_CONTROLLER] != -1) {
			//Shift
			if (mc.currentScreen != null && pressedshift && !lastpressedShift)				
			{
				//press Shift
				mc.currentScreen.pressShiftFake = true;
				if (Display.isActive()) KeyboardSimulator.robot.keyPress(KeyEvent.VK_SHIFT);
			}


			if (mc.currentScreen != null && !pressedshift && lastpressedShift)			
			{
				//release Shift
				mc.currentScreen.pressShiftFake = false;
				if (Display.isActive()) KeyboardSimulator.robot.keyRelease(KeyEvent.VK_SHIFT);
			}	
			//end Shift
		}

		if (controllerMouseX >= 0 && controllerMouseX < mc.displayWidth
				&& controllerMouseY >=0 && controllerMouseY < mc.displayHeight)
		{
			// mouse on screen
			int mouseX = Math.min(Math.max((int) controllerMouseX, 0), mc.displayWidth);
			int mouseY = Math.min(Math.max((int) controllerMouseY, 0), mc.displayHeight);

			if (controllerDeviceIndex[RIGHT_CONTROLLER] != -1)
			{
				InputInjector.mouseMoveEvent(mouseX, mouseY); // Needs to be called first, since it only puts an event if delta != 0
				Mouse.setCursorPosition(mouseX, mouseY);
				controllerMouseValid = true;

				//LMB
				if (mc.currentScreen != null && pressedleftclick && !lastpressedleftclick)
				{ //press left mouse button
					if (Display.isActive()) 
						KeyboardSimulator.robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
					else mc.currentScreen.mouseDown(mouseX, mouseY, 0);
				}	

				if (mc.currentScreen != null && pressedleftclick)				
					mc.currentScreen.mouseDrag(mouseX, mouseY);//Signals mouse move


				if (mc.currentScreen != null && !pressedleftclick && lastpressedleftclick) {
					//release left mouse button
					if (Display.isActive()) KeyboardSimulator.robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
					else mc.currentScreen.mouseUp(mouseX, mouseY, 0);
				}
				//end LMB

				//RMB
				if (mc.currentScreen != null && pressedrightclick && !lastpressedrightclick) {
					//press right mouse button
					if (Display.isActive()) KeyboardSimulator.robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
					else mc.currentScreen.mouseDown(mouseX, mouseY, 1);
				}	

				if (mc.currentScreen != null && pressedrightclick)
					mc.currentScreen.mouseDrag(mouseX, mouseY);//Signals mouse move

				if (mc.currentScreen != null && !pressedrightclick && lastpressedrightclick) {
					//release right mouse button
					if (Display.isActive()) KeyboardSimulator.robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
					else mc.currentScreen.mouseUp(mouseX, mouseY, 1);
				}	
				//end RMB	

				//MMB
				if (mc.currentScreen != null && pressedmiddleclick && !lastpressedmiddleclick) {
					//press middle mouse button
					if (Display.isActive()) KeyboardSimulator.robot.mousePress(InputEvent.BUTTON2_DOWN_MASK);
					else mc.currentScreen.mouseDown(mouseX, mouseY, 2);
				}	

				if (mc.currentScreen != null && pressedmiddleclick) 
					mc.currentScreen.mouseDrag(mouseX, mouseY);//Signals mouse move

				if (mc.currentScreen != null && !pressedmiddleclick && lastpressedmiddleclick) {
					//release middle mouse button
					if (Display.isActive()) KeyboardSimulator.robot.mouseRelease(InputEvent.BUTTON2_DOWN_MASK);
					else mc.currentScreen.mouseUp(mouseX, mouseY, 2);
				}	
				//end MMB

			}
		} else { //mouse off screen
			if(controllerMouseTicks == 0)
				controllerMouseValid = false;

			if(controllerMouseTicks>0)controllerMouseTicks--;

			if (mc.player != null && !(mc.currentScreen instanceof GuiWinGame))
			{
				boolean pressedRMB = pressedrightclick && !lastpressedrightclick;
				boolean pressedLMB = pressedleftclick && !lastpressedleftclick;

				if (pressedLMB || pressedRMB)
				{
					//mc.player.closeScreen();
				}
			}
		}
	}
	
	public static void destroy()
	{
		if (initialized)
		{
			try {
				JOpenVRLibrary.VR_ShutdownInternal();
				initialized = false;
				if(Main.katvr)
					jkatvr.Halt();
			} catch (Exception e) {
				// TODO: handle exception
			}

		}
	}

//	public HmdParameters getHMDInfo()
//	{
//		HmdParameters hmd = new HmdParameters();
//		if ( isInitialized() )
//		{
//			IntBuffer rtx = IntBuffer.allocate(1);
//			IntBuffer rty = IntBuffer.allocate(1);
//			vrsystem.GetRecommendedRenderTargetSize.apply(rtx, rty);
//
//			hmd.Type = HmdType.ovrHmd_Other;
//			hmd.ProductName = "OpenVR";
//			hmd.Manufacturer = "Unknown";
//			hmd.AvailableHmdCaps = 0;
//			hmd.DefaultHmdCaps = 0;
//			hmd.AvailableTrackingCaps = HmdParameters.ovrTrackingCap_Orientation | HmdParameters.ovrTrackingCap_Position;
//			hmd.DefaultTrackingCaps = HmdParameters.ovrTrackingCap_Orientation | HmdParameters.ovrTrackingCap_Position;
//			hmd.Resolution = new Sizei( rtx.get(0) * 2, rty.get(0) );
//
//			float topFOV = vrsystem.GetFloatTrackedDeviceProperty.apply(JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd, JOpenVRLibrary.ETrackedDeviceProperty.ETrackedDeviceProperty_Prop_FieldOfViewTopDegrees_Float, hmdErrorStore);
//			float bottomFOV = vrsystem.GetFloatTrackedDeviceProperty.apply(JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd, JOpenVRLibrary.ETrackedDeviceProperty.ETrackedDeviceProperty_Prop_FieldOfViewBottomDegrees_Float, hmdErrorStore);
//			float leftFOV = vrsystem.GetFloatTrackedDeviceProperty.apply(JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd, JOpenVRLibrary.ETrackedDeviceProperty.ETrackedDeviceProperty_Prop_FieldOfViewLeftDegrees_Float, hmdErrorStore);
//			float rightFOV = vrsystem.GetFloatTrackedDeviceProperty.apply(JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd, JOpenVRLibrary.ETrackedDeviceProperty.ETrackedDeviceProperty_Prop_FieldOfViewRightDegrees_Float, hmdErrorStore);
//
//			hmd.DefaultEyeFov[0] = new FovPort((float)Math.tan(topFOV),(float)Math.tan(bottomFOV),(float)Math.tan(leftFOV),(float)Math.tan(rightFOV));
//			hmd.DefaultEyeFov[1] = new FovPort((float)Math.tan(topFOV),(float)Math.tan(bottomFOV),(float)Math.tan(leftFOV),(float)Math.tan(rightFOV));
//			hmd.MaxEyeFov[0] = new FovPort((float)Math.tan(topFOV),(float)Math.tan(bottomFOV),(float)Math.tan(leftFOV),(float)Math.tan(rightFOV));
//			hmd.MaxEyeFov[1] = new FovPort((float)Math.tan(topFOV),(float)Math.tan(bottomFOV),(float)Math.tan(leftFOV),(float)Math.tan(rightFOV));
//			hmd.DisplayRefreshRate = 90.0f;
//		}
//
//		return hmd;
//	}



	private static void findControllerDevices()
	{
		controllerDeviceIndex[RIGHT_CONTROLLER] = -1;
		controllerDeviceIndex[LEFT_CONTROLLER] = -1;

		if(mc.vrSettings.vrReverseHands){
			controllerDeviceIndex[RIGHT_CONTROLLER]  = vrsystem.GetTrackedDeviceIndexForControllerRole.apply(JOpenVRLibrary.ETrackedControllerRole.ETrackedControllerRole_TrackedControllerRole_LeftHand);
			controllerDeviceIndex[LEFT_CONTROLLER] = vrsystem.GetTrackedDeviceIndexForControllerRole.apply(JOpenVRLibrary.ETrackedControllerRole.ETrackedControllerRole_TrackedControllerRole_RightHand);
		}else {
			controllerDeviceIndex[LEFT_CONTROLLER]  = vrsystem.GetTrackedDeviceIndexForControllerRole.apply(JOpenVRLibrary.ETrackedControllerRole.ETrackedControllerRole_TrackedControllerRole_LeftHand);
			controllerDeviceIndex[RIGHT_CONTROLLER] = vrsystem.GetTrackedDeviceIndexForControllerRole.apply(JOpenVRLibrary.ETrackedControllerRole.ETrackedControllerRole_TrackedControllerRole_RightHand);
		}
		
	}

	private static void updateControllerButtonState()
	{
		for (int c = 0; c < 2; c++) //each controller
		{
			// store previous state
			lastControllerState[c].unPacketNum = controllerStateReference[c].unPacketNum;
			lastControllerState[c].ulButtonPressed = controllerStateReference[c].ulButtonPressed;
			lastControllerState[c].ulButtonTouched = controllerStateReference[c].ulButtonTouched;

			for (int i = 0; i < 5; i++) //5 axes but only [0] and [1] is anything, trigger and touchpad
			{
				if (controllerStateReference[c].rAxis[i] != null)
				{
					lastControllerState[c].rAxis[i].x = controllerStateReference[c].rAxis[i].x;
					lastControllerState[c].rAxis[i].y = controllerStateReference[c].rAxis[i].y;
				}
			}

			// read new state
			if (controllerDeviceIndex[c] != -1)
			{			
				vrsystem.GetControllerState.apply(controllerDeviceIndex[c], inputStateRefernceArray[c], inputStateRefernceArray[c].size());
				inputStateRefernceArray[c].read();
				controllerStateReference[c] = inputStateRefernceArray[c];			
			} else
			{
				// controller not connected, clear state
				lastControllerState[c].ulButtonPressed = 0;
				lastControllerState[c].ulButtonPressed = 0;

				for (int i = 0; i < 5; i++)
				{
					if (controllerStateReference[c].rAxis[i] != null)
					{
						lastControllerState[c].rAxis[i].x = 0.0f;
						lastControllerState[c].rAxis[i].y = 0.0f;
					}
				}
				try{
					controllerStateReference[c] = lastControllerState[c];					
				} catch (Throwable e){

				}
			}
		}
	}


	//OK the fundamental problem with this is Minecraft uses a LWJGL event buffer for keyboard and mouse inputs. It polls those devices faster
	//and presents the game with a nice queue of things that happened. With OpenVR we're polling the controllers directly on the -game- (edit render?) loop.
	//This means we should only set keys as pressed when they change state, or they will repeat.
	//And we should still unpress the key when released.
	//TODO: make a new class that polls more quickly and provides Minecraft.java with a HTCController.next() event queue. (unless openVR has one?)
	private static void processControllerButtons(boolean sleeping, boolean gui)
	{
	// right controller
		//last
		boolean lastpressedRGrip = (lastControllerState[RIGHT_CONTROLLER].ulButtonPressed & k_buttonGrip) > 0;		
		boolean lastpressedRtouchpadBottomLeft = (lastControllerState[RIGHT_CONTROLLER].ulButtonPressed & k_buttonTouchpad) > 0 &&
				(lastControllerState[RIGHT_CONTROLLER].rAxis[k_EAxis_TouchPad].y < 0 ) &&
				(lastControllerState[RIGHT_CONTROLLER].rAxis[k_EAxis_TouchPad].x < 0 ) ;	
		boolean lastpressedRtouchpadBottomRight = (lastControllerState[RIGHT_CONTROLLER].ulButtonPressed & k_buttonTouchpad) > 0 &&
				(lastControllerState[RIGHT_CONTROLLER].rAxis[k_EAxis_TouchPad].y < 0 ) &&
				(lastControllerState[RIGHT_CONTROLLER].rAxis[k_EAxis_TouchPad].x > 0 ) ;		
		boolean lastpressedRtouchpadTopLeft = (lastControllerState[RIGHT_CONTROLLER].ulButtonPressed & k_buttonTouchpad) > 0 &&
				(lastControllerState[RIGHT_CONTROLLER].rAxis[k_EAxis_TouchPad].y > 0 ) &&
				(lastControllerState[RIGHT_CONTROLLER].rAxis[k_EAxis_TouchPad].x < 0 ) ;	
		boolean lastpressedRtouchpadTopRight = (lastControllerState[RIGHT_CONTROLLER].ulButtonPressed & k_buttonTouchpad) > 0 &&
				(lastControllerState[RIGHT_CONTROLLER].rAxis[k_EAxis_TouchPad].y > 0 ) &&
				(lastControllerState[RIGHT_CONTROLLER].rAxis[k_EAxis_TouchPad].x > 0 ) ;		
		boolean lastpressedRAppMenu = (lastControllerState[RIGHT_CONTROLLER].ulButtonPressed & k_buttonAppMenu) > 0;
		boolean lastpressedRTrigger = lastControllerState[RIGHT_CONTROLLER].rAxis[k_EAxis_Trigger].x > triggerThreshold;		
		boolean lastpressedRTriggerClick = lastControllerState[RIGHT_CONTROLLER].rAxis[k_EAxis_Trigger].x > 0.99F;
		//current
		boolean pressedRGrip = (controllerStateReference[RIGHT_CONTROLLER].ulButtonPressed & k_buttonGrip) > 0;
		boolean pressedRtouchpadBottomLeft = (controllerStateReference[RIGHT_CONTROLLER].ulButtonPressed & k_buttonTouchpad) > 0 &&
				(controllerStateReference[RIGHT_CONTROLLER].rAxis[k_EAxis_TouchPad].y < 0 ) &&
				(controllerStateReference[RIGHT_CONTROLLER].rAxis[k_EAxis_TouchPad].x < 0 ) ;	
		boolean pressedRtouchpadBottomRight = (controllerStateReference[RIGHT_CONTROLLER].ulButtonPressed & k_buttonTouchpad) > 0 &&
				(controllerStateReference[RIGHT_CONTROLLER].rAxis[k_EAxis_TouchPad].y < 0 ) &&
				(controllerStateReference[RIGHT_CONTROLLER].rAxis[k_EAxis_TouchPad].x > 0 ) ;		
		boolean pressedRtouchpadTopLeft = (controllerStateReference[RIGHT_CONTROLLER].ulButtonPressed & k_buttonTouchpad) > 0 &&
				(controllerStateReference[RIGHT_CONTROLLER].rAxis[k_EAxis_TouchPad].y > 0 ) &&
				(controllerStateReference[RIGHT_CONTROLLER].rAxis[k_EAxis_TouchPad].x < 0 ) ;	
		boolean pressedRtouchpadTopRight = (controllerStateReference[RIGHT_CONTROLLER].ulButtonPressed & k_buttonTouchpad) > 0 &&
				(controllerStateReference[RIGHT_CONTROLLER].rAxis[k_EAxis_TouchPad].y > 0 ) &&
				(controllerStateReference[RIGHT_CONTROLLER].rAxis[k_EAxis_TouchPad].x > 0 ) ;	
		boolean pressedRAppMenu = (controllerStateReference[RIGHT_CONTROLLER].ulButtonPressed & k_buttonAppMenu) > 0;
		boolean pressedRTrigger = controllerStateReference[RIGHT_CONTROLLER].rAxis[k_EAxis_Trigger].x > triggerThreshold;
		boolean pressedRTriggerClick = controllerStateReference[RIGHT_CONTROLLER].rAxis[k_EAxis_Trigger].x > 0.99F;

		if(!gui){ 
			//R GRIP
			if (pressedRGrip && !lastpressedRGrip) {
				mc.vrSettings.buttonMappings[ViveButtons.BUTTON_RIGHT_GRIP.ordinal()].press();
			}	
			//R TOUCHPAD	
			if (pressedRtouchpadBottomLeft && !lastpressedRtouchpadBottomLeft){
				mc.vrSettings.buttonMappings[ViveButtons.BUTTON_RIGHT_TOUCHPAD_BL.ordinal()].press();
			}			
			if (pressedRtouchpadBottomRight && !lastpressedRtouchpadBottomRight){
				mc.vrSettings.buttonMappings[ViveButtons.BUTTON_RIGHT_TOUCHPAD_BR.ordinal()].press();
			}			
			if (pressedRtouchpadTopLeft && !lastpressedRtouchpadTopLeft){
				mc.vrSettings.buttonMappings[ViveButtons.BUTTON_RIGHT_TOUCHPAD_UL.ordinal()].press();		
			}				
			if (pressedRtouchpadTopRight && !lastpressedRtouchpadTopRight ){
				mc.vrSettings.buttonMappings[ViveButtons.BUTTON_RIGHT_TOUCHPAD_UR.ordinal()].press();		
			}			
			//R TRIGGER
			if (pressedRTrigger && !lastpressedRTrigger) {
				mc.vrSettings.buttonMappings[ViveButtons.BUTTON_RIGHT_TRIGGER.ordinal()].press();
			}
			//R AppMenu
			if (pressedRAppMenu && !lastpressedRAppMenu) {
				mc.vrSettings.buttonMappings[ViveButtons.BUTTON_RIGHT_APPMENU.ordinal()].press();
			}	
			//R triggerclick
			if (pressedRTriggerClick && !lastpressedRTriggerClick) {
				mc.vrSettings.buttonMappings[ViveButtons.BUTTON_RIGHT_TRIGGER_FULLCLICK.ordinal()].press();
			}	
		}
		
		if(!pressedRGrip && lastpressedRGrip) {
			mc.vrSettings.buttonMappings[ViveButtons.BUTTON_RIGHT_GRIP.ordinal()].unpress();
		}
		//R TOUCHPAD	
		if (!pressedRtouchpadBottomLeft && lastpressedRtouchpadBottomLeft){
			mc.vrSettings.buttonMappings[ViveButtons.BUTTON_RIGHT_TOUCHPAD_BL.ordinal()].unpress();
		}	
		if (!pressedRtouchpadBottomRight && lastpressedRtouchpadBottomRight){
			mc.vrSettings.buttonMappings[ViveButtons.BUTTON_RIGHT_TOUCHPAD_BR.ordinal()].unpress();
		}	
		if (!pressedRtouchpadTopLeft && lastpressedRtouchpadTopLeft){
			mc.vrSettings.buttonMappings[ViveButtons.BUTTON_RIGHT_TOUCHPAD_UL.ordinal()].unpress();
		}
		if (!pressedRtouchpadTopRight  && lastpressedRtouchpadTopRight ){
			mc.vrSettings.buttonMappings[ViveButtons.BUTTON_RIGHT_TOUCHPAD_UR.ordinal()].unpress();
		}	
		//R TRIGGER
		if(!pressedRTrigger && lastpressedRTrigger) {
			mc.vrSettings.buttonMappings[ViveButtons.BUTTON_RIGHT_TRIGGER.ordinal()].unpress();
		}
		if(!pressedRAppMenu && lastpressedRAppMenu) {
			mc.vrSettings.buttonMappings[ViveButtons.BUTTON_RIGHT_APPMENU.ordinal()].unpress();
		}
		if(!pressedRTriggerClick && lastpressedRTriggerClick) {
			mc.vrSettings.buttonMappings[ViveButtons.BUTTON_RIGHT_TRIGGER_FULLCLICK.ordinal()].unpress();
		}			

		// left controller
		//last
		boolean lastpressedLGrip = (lastControllerState[LEFT_CONTROLLER].ulButtonPressed & k_buttonGrip) > 0;		
		boolean lastpressedLtouchpadBottomLeft = (lastControllerState[LEFT_CONTROLLER].ulButtonPressed & k_buttonTouchpad) > 0 &&
				(lastControllerState[LEFT_CONTROLLER].rAxis[k_EAxis_TouchPad].y < 0 ) &&
				(lastControllerState[LEFT_CONTROLLER].rAxis[k_EAxis_TouchPad].x < 0 ) ;	
		boolean lastpressedLtouchpadBottomRight = (lastControllerState[LEFT_CONTROLLER].ulButtonPressed & k_buttonTouchpad) > 0 &&
				(lastControllerState[LEFT_CONTROLLER].rAxis[k_EAxis_TouchPad].y < 0 ) &&
				(lastControllerState[LEFT_CONTROLLER].rAxis[k_EAxis_TouchPad].x > 0 ) ;		
		boolean lastpressedLtouchpadTopLeft = (lastControllerState[LEFT_CONTROLLER].ulButtonPressed & k_buttonTouchpad) > 0 &&
				(lastControllerState[LEFT_CONTROLLER].rAxis[k_EAxis_TouchPad].y > 0 ) &&
				(lastControllerState[LEFT_CONTROLLER].rAxis[k_EAxis_TouchPad].x < 0 ) ;	
		boolean lastpressedLtouchpadTopRight = (lastControllerState[LEFT_CONTROLLER].ulButtonPressed & k_buttonTouchpad) > 0 &&
				(lastControllerState[LEFT_CONTROLLER].rAxis[k_EAxis_TouchPad].y > 0 ) &&
				(lastControllerState[LEFT_CONTROLLER].rAxis[k_EAxis_TouchPad].x > 0 ) ;		
		boolean lastpressedLAppMenu = (lastControllerState[LEFT_CONTROLLER].ulButtonPressed & k_buttonAppMenu) > 0;
		boolean lastpressedLTrigger = lastControllerState[LEFT_CONTROLLER].rAxis[k_EAxis_Trigger].x > triggerThreshold;		
		boolean lastpressedLTriggerClick = lastControllerState[LEFT_CONTROLLER].rAxis[k_EAxis_Trigger].x > 0.99F;
		//current
		boolean pressedLGrip = (controllerStateReference[LEFT_CONTROLLER].ulButtonPressed & k_buttonGrip) > 0;
		boolean pressedLtouchpadBottomLeft = (controllerStateReference[LEFT_CONTROLLER].ulButtonPressed & k_buttonTouchpad) > 0 &&
				(controllerStateReference[LEFT_CONTROLLER].rAxis[k_EAxis_TouchPad].y < 0 ) &&
				(controllerStateReference[LEFT_CONTROLLER].rAxis[k_EAxis_TouchPad].x < 0 ) ;	
		boolean pressedLtouchpadBottomRight = (controllerStateReference[LEFT_CONTROLLER].ulButtonPressed & k_buttonTouchpad) > 0 &&
				(controllerStateReference[LEFT_CONTROLLER].rAxis[k_EAxis_TouchPad].y < 0 ) &&
				(controllerStateReference[LEFT_CONTROLLER].rAxis[k_EAxis_TouchPad].x > 0 ) ;		
		boolean pressedLtouchpadTopLeft = (controllerStateReference[LEFT_CONTROLLER].ulButtonPressed & k_buttonTouchpad) > 0 &&
				(controllerStateReference[LEFT_CONTROLLER].rAxis[k_EAxis_TouchPad].y > 0 ) &&
				(controllerStateReference[LEFT_CONTROLLER].rAxis[k_EAxis_TouchPad].x < 0 ) ;	
		boolean pressedLtouchpadTopRight = (controllerStateReference[LEFT_CONTROLLER].ulButtonPressed & k_buttonTouchpad) > 0 &&
				(controllerStateReference[LEFT_CONTROLLER].rAxis[k_EAxis_TouchPad].y > 0 ) &&
				(controllerStateReference[LEFT_CONTROLLER].rAxis[k_EAxis_TouchPad].x > 0 ) ;	
		boolean pressedLAppMenu = (controllerStateReference[LEFT_CONTROLLER].ulButtonPressed & k_buttonAppMenu) > 0;
		boolean pressedLTrigger = controllerStateReference[LEFT_CONTROLLER].rAxis[k_EAxis_Trigger].x > triggerThreshold;
		boolean pressedLTriggerClick = controllerStateReference[LEFT_CONTROLLER].rAxis[k_EAxis_Trigger].x > 0.99F;


		if(!gui){
			//l GRIP - no gui cause shift.
			if (pressedLGrip && !lastpressedLGrip) {
				mc.vrSettings.buttonMappings[ViveButtons.BUTTON_LEFT_GRIP.ordinal()].press();
			}		

		}
		
		if (pressedLtouchpadBottomLeft && !lastpressedLtouchpadBottomLeft){
			mc.vrSettings.buttonMappings[ViveButtons.BUTTON_LEFT_TOUCHPAD_BL.ordinal()].press();
		}	
		if (pressedLtouchpadBottomRight && !lastpressedLtouchpadBottomRight){
			mc.vrSettings.buttonMappings[ViveButtons.BUTTON_LEFT_TOUCHPAD_BR.ordinal()].press();
		}	
		if (pressedLtouchpadTopLeft && !lastpressedLtouchpadTopLeft){
			mc.vrSettings.buttonMappings[ViveButtons.BUTTON_LEFT_TOUCHPAD_UL.ordinal()].press();		
		}
		if (pressedLtouchpadTopRight && !lastpressedLtouchpadTopRight ){
			mc.vrSettings.buttonMappings[ViveButtons.BUTTON_LEFT_TOUCHPAD_UR.ordinal()].press();		
		}
		//L TRIGGER
		if (pressedLTrigger && !lastpressedLTrigger) {
			mc.vrSettings.buttonMappings[ViveButtons.BUTTON_LEFT_TRIGGER.ordinal()].press();
		}	
		if (pressedLAppMenu && !lastpressedLAppMenu) {
			mc.vrSettings.buttonMappings[ViveButtons.BUTTON_LEFT_APPMENU.ordinal()].press();
		}	
		//L triggerclick
		if (pressedLTriggerClick && !lastpressedLTriggerClick) {
			mc.vrSettings.buttonMappings[ViveButtons.BUTTON_LEFT_TRIGGER_FULLCLICK.ordinal()].press();
		}	

		if(!pressedLGrip && lastpressedLGrip) {
			mc.vrSettings.buttonMappings[ViveButtons.BUTTON_LEFT_GRIP.ordinal()].unpress();
		}
		if (!pressedLtouchpadBottomLeft && lastpressedLtouchpadBottomLeft){
			mc.vrSettings.buttonMappings[ViveButtons.BUTTON_LEFT_TOUCHPAD_BL.ordinal()].unpress();
		}		
		if (!pressedLtouchpadBottomRight && lastpressedLtouchpadBottomRight){
			mc.vrSettings.buttonMappings[ViveButtons.BUTTON_LEFT_TOUCHPAD_BR.ordinal()].unpress();
		}	
		if (!pressedLtouchpadTopLeft && lastpressedLtouchpadTopLeft){
			mc.vrSettings.buttonMappings[ViveButtons.BUTTON_LEFT_TOUCHPAD_UL.ordinal()].unpress();
		}	
		if (!pressedLtouchpadTopRight  && lastpressedLtouchpadTopRight ){
			mc.vrSettings.buttonMappings[ViveButtons.BUTTON_LEFT_TOUCHPAD_UR.ordinal()].unpress();
		}	
		if(!pressedLTrigger && lastpressedLTrigger) {
			mc.vrSettings.buttonMappings[ViveButtons.BUTTON_LEFT_TRIGGER.ordinal()].unpress();
		}
		if(!pressedLAppMenu && lastpressedLAppMenu) {
			mc.vrSettings.buttonMappings[ViveButtons.BUTTON_LEFT_APPMENU.ordinal()].unpress();
		}
		if(!pressedLTriggerClick && lastpressedLTriggerClick) {
			mc.vrSettings.buttonMappings[ViveButtons.BUTTON_LEFT_TRIGGER_FULLCLICK.ordinal()].unpress();
		}			

		if(pressedLAppMenu  && !lastpressedLAppMenu) { //handle menu directly		
			if(pressedLGrip){				
				setKeyboardOverlayShowing(!keyboardShowing, null);			
			} else{
				if(gui || keyboardShowing){
					if(mc.currentScreen instanceof GuiWinGame){ //from 'esc' key on guiwingame since we cant push it.
						mc.getConnection().sendPacket(new CPacketClientStatus(State.PERFORM_RESPAWN));
						mc.displayGuiScreen((GuiScreen)null);		
					}else {
						if(Display.isActive()){
							KeyboardSimulator.robot.keyPress(KeyEvent.VK_ESCAPE); //window focus... yadda yadda
							KeyboardSimulator.robot.keyRelease(KeyEvent.VK_ESCAPE); //window focus... yadda yadda
						}
						else 
							mc.player.closeScreen();
						setKeyboardOverlayShowing(false, null);
					}
				}else
					
				if(!Main.kiosk)
					if(Display.isActive()){
						KeyboardSimulator.robot.keyPress(KeyEvent.VK_ESCAPE); //window focus... yadda yadda
						KeyboardSimulator.robot.keyRelease(KeyEvent.VK_ESCAPE); //window focus... yadda yadda
					}
					else 
						mc.player.closeScreen();
			}
		}
		
		if(pressedRAppMenu  && !lastpressedRAppMenu) { 
			if(mc.gameSettings.keyBindPickBlock.isKeyDown() && mc.vrSettings.displayMirrorMode == mc.vrSettings.MIRROR_MIXED_REALITY){				
				VRHotkeys.snapMRCam(mc,0);
				mc.vrSettings.saveOptions();
			}
		}
	}

	//TODO: move somewhere else.
	private static void processControllerButtonsOculus(boolean sleeping, boolean gui)
	{
		// right controller
		//last
		boolean lastpressedRHandTrigger = (lastControllerState[RIGHT_CONTROLLER].ulButtonPressed & k_button_HandTrigger) > 0;		
		boolean lastpressedB = (lastControllerState[RIGHT_CONTROLLER].ulButtonPressed & k_buttonAppMenu) > 0;
		boolean lastpressedA = (lastControllerState[RIGHT_CONTROLLER].ulButtonPressed & k_button_A) > 0;
		boolean lastpressedRTrigger = lastControllerState[RIGHT_CONTROLLER].rAxis[k_EAxis_Trigger].x > triggerThreshold;		
		boolean lastpressedRStick = (lastControllerState[RIGHT_CONTROLLER].ulButtonPressed & k_buttonTouchpad) > 0;	

		boolean lastTouchedRHandTrigger = (lastControllerState[RIGHT_CONTROLLER].ulButtonTouched & k_button_HandTrigger) > 0;		
		boolean lastTouchedB = (lastControllerState[RIGHT_CONTROLLER].ulButtonTouched & k_buttonAppMenu) > 0;
		boolean lastTouchedA = (lastControllerState[RIGHT_CONTROLLER].ulButtonTouched & k_button_A) > 0;
		boolean lastTouchedRTrigger = lastControllerState[RIGHT_CONTROLLER].rAxis[k_EAxis_Trigger].x > triggerThreshold;	
		boolean lastTouchedRStick = (lastControllerState[RIGHT_CONTROLLER].ulButtonTouched & k_buttonTouchpad) > 0;

		boolean lastpressedRStickRight  = lastControllerState[RIGHT_CONTROLLER].rAxis[k_EAxis_TouchPad].x  > 0.5 ;		
		boolean lastpressedRStickLeft  = lastControllerState[RIGHT_CONTROLLER].rAxis[k_EAxis_TouchPad].x  < -0.5 ;
		boolean lastpressedRStickDown = lastControllerState[RIGHT_CONTROLLER].rAxis[k_EAxis_TouchPad].y < -0.5 ;		
		boolean lastpressedRStickUp  = lastControllerState[RIGHT_CONTROLLER].rAxis[k_EAxis_TouchPad].y  > 0.5 ;

		//current
		boolean pressedRHandTrigger = (controllerStateReference[RIGHT_CONTROLLER].ulButtonPressed & k_button_HandTrigger) > 0;		
		boolean pressedB = (controllerStateReference[RIGHT_CONTROLLER].ulButtonPressed & k_buttonAppMenu) > 0;
		boolean pressedA = (controllerStateReference[RIGHT_CONTROLLER].ulButtonPressed & k_button_A) > 0;
		boolean pressedRTrigger = controllerStateReference[RIGHT_CONTROLLER].rAxis[k_EAxis_Trigger].x > triggerThreshold;	
		boolean pressedRStick = (controllerStateReference[RIGHT_CONTROLLER].ulButtonPressed & k_buttonTouchpad) > 0;	

		//current
		boolean TouchedRHandTrigger = (controllerStateReference[RIGHT_CONTROLLER].ulButtonTouched & k_button_HandTrigger) > 0;		
		boolean TouchedB = (controllerStateReference[RIGHT_CONTROLLER].ulButtonTouched & k_buttonAppMenu) > 0;
		boolean TouchedA = (controllerStateReference[RIGHT_CONTROLLER].ulButtonTouched & k_button_A) > 0;
		boolean TouchedRTrigger = controllerStateReference[RIGHT_CONTROLLER].rAxis[k_EAxis_Trigger].x > triggerThreshold;
		boolean TouchedRStick = (controllerStateReference[RIGHT_CONTROLLER].ulButtonTouched & k_buttonTouchpad) > 0;

		boolean pressedRStickRight  = controllerStateReference[RIGHT_CONTROLLER].rAxis[k_EAxis_TouchPad].x  > 0.5 ;		
		boolean pressedRStickLeft  = controllerStateReference[RIGHT_CONTROLLER].rAxis[k_EAxis_TouchPad].x  < -0.5 ;
		boolean pressedRStickDown = controllerStateReference[RIGHT_CONTROLLER].rAxis[k_EAxis_TouchPad].y < -0.5 ;		
		boolean pressedRStickUp  = controllerStateReference[RIGHT_CONTROLLER].rAxis[k_EAxis_TouchPad].y  > 0.5 ;

		
		if(!gui){ //ignore the left, right, middle mouse buttons
			//R GRIP
			if (pressedRHandTrigger && !lastpressedRHandTrigger) {
				mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_HAND_TRIGGER_PRESS.ordinal()].press();
			}			
			//R TRIGGER
			if (pressedRTrigger && !lastpressedRTrigger) {
				mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_INDEX_TRIGGER_PRESS.ordinal()].press();
			}					
			//R A
			if (pressedA && !lastpressedA) {
				mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_A_PRESS.ordinal()].press();
			}	
		}
		
		//R B
		if (pressedB && !lastpressedB) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_B_PRESS.ordinal()].press();
		}	
		
		//R GRIP
		if (TouchedRHandTrigger && !lastTouchedRHandTrigger) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_HAND_TRIGGER_TOUCH.ordinal()].press();
		}			
		//R TRIGGER
		if (TouchedRTrigger && !lastTouchedRTrigger) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_INDEX_TRIGGER_TOUCH.ordinal()].press();
		}
	
		//R B
		if (TouchedB && !lastTouchedB) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_B_TOUCH.ordinal()].press();
		}				

		//R A
		if (TouchedA && !lastTouchedA) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_A_TOUCH.ordinal()].press();
		}	
		//R Stick
		if (pressedRStick && !lastpressedRStick) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_STICK_PRESS.ordinal()].press();
		}		

		//R Stick
		if (TouchedRStick && !lastTouchedRStick) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_STICK_TOUCH.ordinal()].press();
		}
		//R Stick Left
		if (pressedRStickLeft && !lastpressedRStickLeft) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_STICK_LEFT.ordinal()].press();
		}
		//R Stick Right
		if (pressedRStickRight && !lastpressedRStickRight) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_STICK_RIGHT.ordinal()].press();
		}
		//R Stick Up
		if (pressedRStickUp && !lastpressedRStickUp) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_STICK_UP.ordinal()].press();
		}
		//R Stick Down
		if (pressedRStickDown && !lastpressedRStickDown) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_STICK_DOWN.ordinal()].press();
		}
		
		
		//R GRIP
		if (!pressedRHandTrigger && lastpressedRHandTrigger) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_HAND_TRIGGER_PRESS.ordinal()].unpress();
		}			
		//R TRIGGER
		if (!pressedRTrigger && lastpressedRTrigger) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_INDEX_TRIGGER_PRESS.ordinal()].unpress();
		}
		//R B
		if (!pressedB && lastpressedB) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_B_PRESS.ordinal()].unpress();
		}				
		//R A
		if (!pressedA && lastpressedA) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_A_PRESS.ordinal()].unpress();
		}	

		//R Stick
		if (!pressedRStick && lastpressedRStick) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_STICK_PRESS.ordinal()].unpress();
		}	

		//R GRIP
		if (!TouchedRHandTrigger && lastTouchedRHandTrigger) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_HAND_TRIGGER_TOUCH.ordinal()].unpress();
		}			
		//R TRIGGER
		if (!TouchedRTrigger && lastTouchedRTrigger) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_INDEX_TRIGGER_TOUCH.ordinal()].unpress();
		}
		//R B
		if (!TouchedB && lastTouchedB) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_B_TOUCH.ordinal()].unpress();
		}				
		//R A
		if (!TouchedA && lastTouchedA) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_A_TOUCH.ordinal()].unpress();
		}	
		//R Stick
		if (!TouchedRStick && lastTouchedRStick) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_STICK_TOUCH.ordinal()].unpress();
		}

		//R Stick Left
		if (!pressedRStickLeft && lastpressedRStickLeft) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_STICK_LEFT.ordinal()].unpress();
		}
		//R Stick Right
		if (!pressedRStickRight && lastpressedRStickRight) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_STICK_RIGHT.ordinal()].unpress();
		}
		//R Stick Up
		if (!pressedRStickUp && lastpressedRStickUp) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_STICK_UP.ordinal()].unpress();
		}
		//R Stick Down
		if (!pressedRStickDown && lastpressedRStickDown) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_RIGHT_STICK_DOWN.ordinal()].unpress();
		}
		
		// LEFT controller
		//last
		boolean lastpressedLHandTrigger = (lastControllerState[LEFT_CONTROLLER].ulButtonPressed & k_buttonGrip) > 0;		
		boolean lastpressedY = (lastControllerState[LEFT_CONTROLLER].ulButtonPressed & k_buttonAppMenu) > 0;
		boolean lastpressedX = (lastControllerState[LEFT_CONTROLLER].ulButtonPressed & k_button_A) > 0;
		boolean lastpressedLTrigger = lastControllerState[LEFT_CONTROLLER].rAxis[k_EAxis_Trigger].x > triggerThreshold;		
		boolean lastpressedLStick = (lastControllerState[LEFT_CONTROLLER].ulButtonPressed & k_buttonTouchpad) > 0;	

		boolean lastTouchedLHandTrigger = (lastControllerState[LEFT_CONTROLLER].ulButtonTouched & k_buttonGrip) > 0;		
		boolean lastTouchedY = (lastControllerState[LEFT_CONTROLLER].ulButtonTouched & k_buttonAppMenu) > 0;
		boolean lastTouchedX = (lastControllerState[LEFT_CONTROLLER].ulButtonTouched & k_button_A) > 0;
		boolean lastTouchedLTrigger = lastControllerState[LEFT_CONTROLLER].rAxis[k_EAxis_Trigger].x > triggerThreshold;	
		boolean lastTouchedLStick = (lastControllerState[LEFT_CONTROLLER].ulButtonTouched & k_buttonTouchpad) > 0;

		boolean lastpressedStickRight  = lastControllerState[LEFT_CONTROLLER].rAxis[k_EAxis_TouchPad].x  > 0.5 ;		
		boolean lastpressedStickLeft  = lastControllerState[LEFT_CONTROLLER].rAxis[k_EAxis_TouchPad].x  < -0.5 ;
		boolean lastpressedStickDown = lastControllerState[LEFT_CONTROLLER].rAxis[k_EAxis_TouchPad].y < -0.5 ;		
		boolean lastpressedStickUp  = lastControllerState[LEFT_CONTROLLER].rAxis[k_EAxis_TouchPad].y  > 0.5 ;

		//current
		boolean pressedLHandTrigger = (controllerStateReference[LEFT_CONTROLLER].ulButtonPressed & k_buttonGrip) > 0;		
		boolean pressedY = (controllerStateReference[LEFT_CONTROLLER].ulButtonPressed & k_buttonAppMenu) > 0;
		boolean pressedX = (controllerStateReference[LEFT_CONTROLLER].ulButtonPressed & k_button_A) > 0;
		boolean pressedLTrigger = controllerStateReference[LEFT_CONTROLLER].rAxis[k_EAxis_Trigger].x > triggerThreshold;	
		boolean pressedLStick = (controllerStateReference[LEFT_CONTROLLER].ulButtonPressed & k_buttonTouchpad) > 0;	

		boolean TouchedLHandTrigger = (controllerStateReference[LEFT_CONTROLLER].ulButtonTouched & k_buttonGrip) > 0;		
		boolean TouchedY = (controllerStateReference[LEFT_CONTROLLER].ulButtonTouched & k_buttonAppMenu) > 0;
		boolean TouchedX = (controllerStateReference[LEFT_CONTROLLER].ulButtonTouched & k_button_A) > 0;
		boolean TouchedLTrigger = controllerStateReference[LEFT_CONTROLLER].rAxis[k_EAxis_Trigger].x > triggerThreshold;	
		boolean TouchedLStick = (controllerStateReference[LEFT_CONTROLLER].ulButtonTouched & k_buttonTouchpad) > 0;


		boolean pressedStickRight  = controllerStateReference[LEFT_CONTROLLER].rAxis[k_EAxis_TouchPad].x  > 0.5 ;		
		boolean pressedStickLeft  = controllerStateReference[LEFT_CONTROLLER].rAxis[k_EAxis_TouchPad].x  < -0.5 ;
		boolean pressedStickDown = controllerStateReference[LEFT_CONTROLLER].rAxis[k_EAxis_TouchPad].y < -0.5 ;		
		boolean pressedStickUp  = controllerStateReference[LEFT_CONTROLLER].rAxis[k_EAxis_TouchPad].y  > 0.5 ;



		rtbX = controllerStateReference[LEFT_CONTROLLER].rAxis[k_EAxis_TouchPad].x;
		rtbY = controllerStateReference[LEFT_CONTROLLER].rAxis[k_EAxis_TouchPad].y;

		//press

		if(!gui){ 
			//L GRIP
			if (pressedLHandTrigger && !lastpressedLHandTrigger) {
				mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_HAND_TRIGGER_PRESS.ordinal()].press();
			}	
		}

		//L TRIGGER
		if (pressedLTrigger && !lastpressedLTrigger) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_INDEX_TRIGGER_PRESS.ordinal()].press();
		}
		//L B
		if (pressedY && !lastpressedY) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_Y_PRESS.ordinal()].press();
		}				
		//L A
		if (pressedX && !lastpressedX) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_X_PRESS.ordinal()].press();
		}	
		//L Stick
		if (pressedLStick && !lastpressedLStick) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_STICK_PRESS.ordinal()].press();
		}	
		//L GRIP
		if (TouchedLHandTrigger && !lastTouchedLHandTrigger) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_HAND_TRIGGER_TOUCH.ordinal()].press();
		}			
		//L TRIGGER
		if (TouchedLTrigger && !lastTouchedLTrigger) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_INDEX_TRIGGER_TOUCH.ordinal()].press();
		}
		//L B
		if (TouchedY && !lastTouchedY) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_Y_TOUCH.ordinal()].press();
		}				
		//L A
		if (TouchedX && !lastTouchedX) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_X_TOUCH.ordinal()].press();
		}		
		//L Stick
		if (TouchedLStick && !lastTouchedLStick) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_STICK_TOUCH.ordinal()].press();
		}
		//L Stick Left
		if (pressedStickLeft && !lastpressedStickLeft) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_STICK_LEFT.ordinal()].press();
		}
		//L Stick Right
		if (pressedStickRight && !lastpressedStickRight) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_STICK_RIGHT.ordinal()].press();
		}
		//L Stick Up
		if (pressedStickUp && !lastpressedStickUp) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_STICK_UP.ordinal()].press();
		}
		//L Stick Down
		if (pressedStickDown && !lastpressedStickDown) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_STICK_DOWN.ordinal()].press();
		}
		//unpress

		//L GRIP
		if (!pressedLHandTrigger && lastpressedLHandTrigger) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_HAND_TRIGGER_PRESS.ordinal()].unpress();
		}			
		//L TRIGGER
		if (!pressedLTrigger && lastpressedLTrigger) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_INDEX_TRIGGER_PRESS.ordinal()].unpress();
		}
		//L B
		if (!pressedY && lastpressedY) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_Y_PRESS.ordinal()].unpress();
		}				
		//L A
		if (!pressedX && lastpressedX) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_X_PRESS.ordinal()].unpress();
		}	
		//L Stick
		if (!pressedLStick && lastpressedLStick) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_STICK_PRESS.ordinal()].unpress();
		}	
		//L GRIP
		if (!TouchedLHandTrigger && lastTouchedLHandTrigger) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_HAND_TRIGGER_TOUCH.ordinal()].unpress();
		}			
		//L TRIGGER
		if (!TouchedLTrigger && lastTouchedLTrigger) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_INDEX_TRIGGER_TOUCH.ordinal()].unpress();
		}
		//L B
		if (!TouchedY && lastTouchedY) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_Y_TOUCH.ordinal()].unpress();
		}				
		//L A
		if (!TouchedX && lastTouchedX) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_X_TOUCH.ordinal()].unpress();
		}	
		//L Stick
		if (!TouchedLStick && lastTouchedLStick) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_STICK_TOUCH.ordinal()].unpress();
		}
		//L Stick Left
		if (!pressedStickLeft && lastpressedStickLeft) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_STICK_LEFT.ordinal()].unpress();
		}
		//L Stick Right
		if (!pressedStickRight && lastpressedStickRight) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_STICK_RIGHT.ordinal()].unpress();
		}
		//L Stick Up
		if (!pressedStickUp && lastpressedStickUp) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_STICK_UP.ordinal()].unpress();
		}
		//L Stick Down
		if (!pressedStickDown && lastpressedStickDown) {
			mc.vrSettings.buttonMappings[ViveButtons.OCULUS_LEFT_STICK_DOWN.ordinal()].unpress();
		}


		if(pressedY  && !lastpressedY) { //handle menu directly		
			if(pressedLHandTrigger){				
				setKeyboardOverlayShowing(!keyboardShowing, null);			
			} else{
				if(gui || keyboardShowing){
					if(mc.currentScreen instanceof GuiWinGame){ //from 'esc' key on guiwingame since we cant push it.
						mc.getConnection().sendPacket(new CPacketClientStatus(State.PERFORM_RESPAWN));
						mc.displayGuiScreen((GuiScreen)null);		
					}else {
						mc.player.closeScreen();
						setKeyboardOverlayShowing(false, null);
					}
				}else

					if(!Main.kiosk)mc.displayInGameMenu();				
			}
		}

		if(pressedA  && !lastpressedA) { //handle menu directly
			if(mc.gameSettings.keyBindPickBlock.isKeyDown() && mc.vrSettings.displayMirrorMode == mc.vrSettings.MIRROR_MIXED_REALITY){				
				VRHotkeys.snapMRCam(mc,0);
				mc.vrSettings.saveOptions();
			}
		}

		if (mc.currentScreen != null) {
			if(pressedRStickUp && !lastpressedRStickUp){
				KeyboardSimulator.robot.mouseWheel(-120);
				MCOpenVR.triggerHapticPulse(0, 100);
			}
			
			if(pressedRStickDown && !lastpressedRStickDown){
				KeyboardSimulator.robot.mouseWheel(120);
				MCOpenVR.triggerHapticPulse(0, 100);
			}
		}
		
	}

	private static void processVRFunctions(boolean sleeping, boolean gui) {
		//VIVE SPECIFIC FUNCTIONALITY
		//TODO: Find a better home for these.	

		if(	Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) return;
		
		//handle movementtoggle
		if (mc.gameSettings.keyBindPickBlock.isKeyDown()) {
			if(mc.vrSettings.vrAllowLocoModeSwotch){
				moveModeSwitchcount++;
				if (moveModeSwitchcount >= 20 * 4) {
					moveModeSwitchcount = 0;					
					mc.vrPlayer.setFreeMove(!mc.vrPlayer.getFreeMove());
				}				
			}
		} else {
			moveModeSwitchcount = 0;
		}
		
		if(rotateLeft.isPressed()){
			mc.vrSettings.vrWorldRotation+=mc.vrSettings.vrWorldRotationIncrement;
			mc.vrSettings.vrWorldRotation = mc.vrSettings.vrWorldRotation % 360;
		}
		
		if(rotateRight.isPressed()){
			mc.vrSettings.vrWorldRotation-=mc.vrSettings.vrWorldRotationIncrement;		
			mc.vrSettings.vrWorldRotation = mc.vrSettings.vrWorldRotation % 360;
		}
		
		if(!gui){
			if(walkabout.isKeyDown()){
				float yaw = aimYaw;
				
				//oh this is ugly. TODO: cache which hand when binding button.
				for (VRControllerButtonMapping vb : mc.vrSettings.buttonMappings) {
					if (vb.key == walkabout) {
						if(vb.Button.name().contains("_LEFT")){
							yaw = laimYaw;
							break;
						}
					}
				}
				
				if (!isWalkingAbout){
					isWalkingAbout = true;
					walkaboutYawStart = mc.vrSettings.vrWorldRotation + yaw;  
				}
				else {
					mc.vrSettings.vrWorldRotation = walkaboutYawStart - yaw;
					mc.vrSettings.vrWorldRotation %= 360; // Prevent stupidly large values (can they even happen here?)
				//	mc.vrPlayer.checkandUpdateRotateScale(true);
				}
			} else {
				isWalkingAbout = false;
			}
		}
		
		if(!gui){
			if(rotateFree.isKeyDown()){
				float yaw = aimYaw;
				
				//oh this is ugly. TODO: cache which hand when binding button.
				for (VRControllerButtonMapping vb : mc.vrSettings.buttonMappings) {
					if (vb.key == rotateFree) {
						if(vb.Button.name().contains("_LEFT")){
							yaw = laimYaw;
							break;
						}
					}
				}
				
				if (!isFreeRotate){
					isFreeRotate = true;
					walkaboutYawStart = mc.vrSettings.vrWorldRotation - yaw;  
				}
				else {
					mc.vrSettings.vrWorldRotation = walkaboutYawStart + yaw;
				//	mc.vrPlayer.checkandUpdateRotateScale(true,0);
				}
			} else {
				isFreeRotate = false;
			}
		}
		
		
		if(hotbarNext.isPressed()) {
			changeHotbar(-1);
			MCOpenVR.triggerHapticPulse(0, 250);
			MCOpenVR.triggerHapticPulse(1, 250);
		}
		
		if(hotbarPrev.isPressed()){
			changeHotbar(1);
			MCOpenVR.triggerHapticPulse(0, 250);
			MCOpenVR.triggerHapticPulse(1, 250);
		}
		
		if(quickTorch.isPressed() && mc.player != null){
		    for (int slot=0;slot<9;slot++)
            {  
		    	ItemStack itemStack = mc.player.inventory.getStackInSlot(slot);
                if (itemStack!=null && itemStack.getUnlocalizedName().equals("tile.torch") && mc.currentScreen == null)
                {
                    quickTorchPreviousSlot = mc.player.inventory.currentItem;
                    mc.player.inventory.currentItem = slot;
                    mc.rightClickMouse();
                    // switch back immediately
                    mc.player.inventory.currentItem = quickTorchPreviousSlot;
                    quickTorchPreviousSlot = -1;
                    break;
                }
            }
        }
		
		// if you start teleporting, close any UI
		if (gui && !sleeping && mc.gameSettings.keyBindForward.isKeyDown() && !(mc.currentScreen instanceof GuiWinGame))
		{
			mc.player.closeScreen();
		}

		if(!mc.gameSettings.keyBindInventory.isKeyDown()){
			startedOpeningInventory = 0;
		}

		//GuiContainer.java only listens directly to the keyboard to close.
		if(gui && !(mc.currentScreen instanceof GuiWinGame) && mc.gameSettings.keyBindInventory.isKeyDown()){ //inventory will repeat open/close while button is held down. TODO: fix.
			if((getCurrentTimeSecs() - startedOpeningInventory) > 0.5) mc.player.closeScreen();
			VRControllerButtonMapping.unpressKey(mc.gameSettings.keyBindInventory); //minecraft.java will open a new window otherwise.
		}

	}

	
	private static void changeHotbar(int dir){
		if (Reflector.forgeExists() && mc.currentScreen == null)
			KeyboardSimulator.robot.mouseWheel(-dir * 120);
		else
			mc.player.inventory.changeCurrentItem(dir);
	}
		
	//jrbuda:: oh hello there you are.
	private static void pollInputEvents()
	{
		if(vrsystem == null) return;

		//TODO: use this for everything, maybe.
		jopenvr.VREvent_t event = new jopenvr.VREvent_t();

		while (vrsystem.PollNextEvent.apply(event, event.size() ) > 0)
		{

			switch (event.eventType) {
			case EVREventType.EVREventType_VREvent_KeyboardClosed:
				//'huzzah'
				keyboardShowing = false;
				if (mc.currentScreen instanceof GuiChat && !mc.vrSettings.seated) {
					GuiTextField field = (GuiTextField)MCReflection.getField(MCReflection.GuiChat_inputField, mc.currentScreen);
					if (field != null) {
						String s = field.getText().trim();
						if (!s.isEmpty()) {
							mc.currentScreen.sendChatMessage(s);
						}
					}
					mc.displayGuiScreen((GuiScreen)null);
				}
				break;
			case EVREventType.EVREventType_VREvent_KeyboardCharInput:
				byte[] inbytes = event.data.getPointer().getByteArray(0, 8);	
				int len = 0;			
				for (byte b : inbytes) {
					if(b>0)len++;
				}
				String str = new String(inbytes,0,len, StandardCharsets.UTF_8);
				if (mc.currentScreen != null && !mc.vrSettings.alwaysSimulateKeyboard) { // experimental, needs testing
					try {
						for (char ch : str.toCharArray()) {
							int[] codes = KeyboardSimulator.getLWJGLCodes(ch);
							int code = codes.length > 0 ? codes[codes.length - 1] : 0;
							if (InputInjector.isSupported()) InputInjector.typeKey(code, ch);
							else mc.currentScreen.keyTypedPublic(ch, code);
							break;
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				} else {
					KeyboardSimulator.type(str); //holy shit it works.
				}
				break;
			case EVREventType.EVREventType_VREvent_Quit:
				mc.shutdown();
				break;
			default:
				break;
			}
		}
	}

	private static void updateTouchpadSampleBuffer()
	{
		for (int c=0;c<2;c++)
		{
			if (controllerStateReference[c].rAxis[k_EAxis_TouchPad]!=null &&
					(controllerStateReference[c].ulButtonTouched & k_buttonTouchpad) > 0)
			{
				int sample = touchpadSampleCount[c] % 5;
				touchpadSamples[c][sample].x = controllerStateReference[c].rAxis[k_EAxis_TouchPad].x;
				touchpadSamples[c][sample].y = controllerStateReference[c].rAxis[k_EAxis_TouchPad].y;
				touchpadSampleCount[c]++;
			} else
			{
				clearTouchpadSampleBuffer(c);
			}
		}
	}

	private static void clearTouchpadSampleBuffer(int controller)
	{
		for (int sample=0;sample<5;sample++)
		{
			touchpadSamples[controller][sample].x = 0;
			touchpadSamples[controller][sample].y = 0;
		}
		touchpadSampleCount[controller] = 0;
		inventory_swipeX[controller] = 0;
		inventory_swipeY[controller] = 0;
	}
	

	private static void processTouchpadSampleBuffer()
	{ 
		for(int c=0;c<2;c++){
			boolean touchpadPressed = (controllerStateReference[c].ulButtonPressed & k_buttonTouchpad) > 0;
			if (touchpadSampleCount[c] > 5 && !touchpadPressed){
				int sample = touchpadSampleCount[c] - 5;
				if (sample < 0)
					sample = 0;
				sample = sample % 5;
				int nextSample = (sample + 1) % 5;

				float deltaY = touchpadSamples[c][nextSample].y - touchpadSamples[c][sample].y;
				float deltaX = touchpadSamples[c][nextSample].x - touchpadSamples[c][sample].x;

				inventory_swipeY[c] += deltaY;
				inventory_swipeX[c] += deltaX;

				float swipeDistancePerInventorySlot = 0.5f;
				if (inventory_swipeX[c] > swipeDistancePerInventorySlot)
				{
					inventory_swipeX[c] -= swipeDistancePerInventorySlot;
					if(c==0)
						mc.vrSettings.buttonMappings[ViveButtons.BUTTON_RIGHT_TOUCHPAD_SWIPE_RIGHT.ordinal()].press();
					else 
						mc.vrSettings.buttonMappings[ViveButtons.BUTTON_LEFT_TOUCHPAD_SWIPE_RIGHT.ordinal()].press();
				} else if (inventory_swipeX[c] < -swipeDistancePerInventorySlot)
				{
					inventory_swipeX[c] += swipeDistancePerInventorySlot;
					if(c==0)
						mc.vrSettings.buttonMappings[ViveButtons.BUTTON_RIGHT_TOUCHPAD_SWIPE_LEFT.ordinal()].press();
					else 
						mc.vrSettings.buttonMappings[ViveButtons.BUTTON_LEFT_TOUCHPAD_SWIPE_LEFT.ordinal()].press();
				}

				if (inventory_swipeY[c] > swipeDistancePerInventorySlot)
				{
					inventory_swipeY[c] -= swipeDistancePerInventorySlot;
					if(c==0){
						if (mc.currentScreen != null){
							MCOpenVR.triggerHapticPulse(0, 100);
							KeyboardSimulator.robot.mouseWheel(-120); //still hardcoded GUI scrolling
						}else
							mc.vrSettings.buttonMappings[ViveButtons.BUTTON_RIGHT_TOUCHPAD_SWIPE_UP.ordinal()].press();
					}else
						mc.vrSettings.buttonMappings[ViveButtons.BUTTON_LEFT_TOUCHPAD_SWIPE_UP.ordinal()].press();


				} else if (inventory_swipeY[c] < -swipeDistancePerInventorySlot)
				{
					inventory_swipeY[c] += swipeDistancePerInventorySlot;
					if(c==0){
						if (mc.currentScreen != null){
							MCOpenVR.triggerHapticPulse(0, 100);
							KeyboardSimulator.robot.mouseWheel(120); //still hardcoded GUI scrolling
						}else
							mc.vrSettings.buttonMappings[ViveButtons.BUTTON_RIGHT_TOUCHPAD_SWIPE_DOWN.ordinal()].press();
					}else
						mc.vrSettings.buttonMappings[ViveButtons.BUTTON_LEFT_TOUCHPAD_SWIPE_DOWN.ordinal()].press();
				}		
			}
		}
	}


	private static void updatePose()
	{
		if ( vrsystem == null || vrCompositor == null )
			return;
		int ret = vrCompositor.WaitGetPoses.apply(hmdTrackedDevicePoseReference, JOpenVRLibrary.k_unMaxTrackedDeviceCount, null, 0);

		if(ret>0)System.out.println("Compositor Error: GetPoseError " + OpenVRStereoRenderer.getCompostiorError(ret)); 
				
		if(ret == 101){ //this is so dumb but it works.
			triggerHapticPulse(0, 500);
			triggerHapticPulse(1, 500);
		}
		
		controllerDeviceIndex[THIRD_CONTROLLER] = -1;
		findControllerDevices(); //todo dont do this @90hz

		for (int nDevice = 0; nDevice < JOpenVRLibrary.k_unMaxTrackedDeviceCount; ++nDevice )
		{
			hmdTrackedDevicePoses[nDevice].read();
			if ( hmdTrackedDevicePoses[nDevice].bPoseIsValid != 0 )
			{
				jopenvr.OpenVRUtil.convertSteamVRMatrix3ToMatrix4f(hmdTrackedDevicePoses[nDevice].mDeviceToAbsoluteTracking, poseMatrices[nDevice]);
				deviceVelocity[nDevice] = new Vec3d(hmdTrackedDevicePoses[nDevice].vVelocity.v[0],hmdTrackedDevicePoses[nDevice].vVelocity.v[1],hmdTrackedDevicePoses[nDevice].vVelocity.v[2]);
			}		

			if(mc.vrSettings.displayMirrorMode == VRSettings.MIRROR_MIXED_REALITY){
				if(controllerDeviceIndex[0]!= -1 && controllerDeviceIndex[1] != -1 ){
					int c = vrsystem.GetTrackedDeviceClass.apply(nDevice);
					int r = vrsystem.GetControllerRoleForTrackedDeviceIndex.apply(nDevice);
					if((c == 2 && r == 0) || c == 3)
						controllerDeviceIndex[THIRD_CONTROLLER] = nDevice;
				} 
			}

		}
		if (hmdTrackedDevicePoses[JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd].bPoseIsValid != 0 )
		{
			OpenVRUtil.Matrix4fCopy(poseMatrices[JOpenVRLibrary.k_unTrackedDeviceIndex_Hmd], hmdPose);
			headIsTracking = true;
		}
		else
		{
			headIsTracking = false;
			OpenVRUtil.Matrix4fSetIdentity(hmdPose);
			hmdPose.M[1][3] = 1.62f;
		}

		for (int c=0;c<3;c++)
		{
			if (controllerDeviceIndex[c] != -1)
			{
				controllerTracking[c] = true;
				OpenVRUtil.Matrix4fCopy(poseMatrices[controllerDeviceIndex[c]], controllerPose[c]);
			}
			else
			{
				controllerTracking[c] = false;
				//OpenVRUtil.Matrix4fSetIdentity(controllerPose[c]);
			}
		}

		getTipTransforms(); //TODO dont do this @90hz.

		updateAim();
		//VRHotkeys.snapMRCam(mc, 0);
	
	}

	/**
	 * @return The coordinate of the 'center' eye position relative to the head yaw plane
	 */
	
	public static Vec3d getCenterEyePosition() {
		Vector3f pos = OpenVRUtil.convertMatrix4ftoTranslationVector(hmdPose);
		pos=pos.add(offset);
		return new Vec3d(pos.x, pos.y, pos.z);
	}

	/**
	 * @return The coordinate of the left or right eye position relative to the head yaw plane
	 */
	
	static Vec3d getEyePosition(renderPass eye)
	{
		Matrix4f hmdToEye = hmdPoseRightEye;
		if ( eye == renderPass.Left )
		{
			hmdToEye = hmdPoseLeftEye;
		} else if ( eye == renderPass.Right)
		{
			hmdToEye = hmdPoseRightEye;
		} else {
			hmdToEye = null;
		}
			
		if(hmdToEye == null){
			Matrix4f pose = hmdPose;
			Vector3f pos = OpenVRUtil.convertMatrix4ftoTranslationVector(pose);
			pos=pos.add(offset);
			return new Vec3d(pos.x, pos.y, pos.z);
		} else {
			Matrix4f pose = Matrix4f.multiply( hmdPose, hmdToEye );
			Vector3f pos = OpenVRUtil.convertMatrix4ftoTranslationVector(pose);
			pos=pos.add(offset);
			return new Vec3d(pos.x, pos.y, pos.z);
		}
	}


	/**
	 * Gets the Yaw(Y) from YXZ Euler angle representation of orientation
	 *
	 * @return The Head Yaw, in degrees
	 */
	
	static float getHeadYawDegrees()
	{
		Quatf quat = OpenVRUtil.convertMatrix4ftoRotationQuat(hmdPose);

		EulerOrient euler = OpenVRUtil.getEulerAnglesDegYXZ(quat);

		return euler.yaw;
	}

	/**
	 * Gets the Pitch(X) from YXZ Euler angle representation of orientation
	 *
	 * @return The Head Pitch, in degrees
	 */
	
	static float getHeadPitchDegrees()
	{
		Quatf quat = OpenVRUtil.convertMatrix4ftoRotationQuat(hmdPose);

		EulerOrient euler = OpenVRUtil.getEulerAnglesDegYXZ(quat);

		return euler.pitch;
	}

	/**
	 *
	 * @return Play area size or null if not valid
	 */
	public static float[] getPlayAreaSize() {
		if (vrChaperone == null || vrChaperone.GetPlayAreaSize == null) return null;
		FloatByReference bufz = new FloatByReference();
		FloatByReference bufx = new FloatByReference();
		byte valid = vrChaperone.GetPlayAreaSize.apply(bufx, bufz);
		if (valid == 1) return new float[]{bufx.getValue(), bufz.getValue()};
		return null;
	}

	/**
	 * Gets the orientation quaternion
	 *
	 * @return quaternion w, x, y & z components
	 */
	
	static EulerOrient getOrientationEuler()
	{
		Quatf orient = OpenVRUtil.convertMatrix4ftoRotationQuat(hmdPose);
		return OpenVRUtil.getEulerAnglesDegYXZ(orient);
	}
		
	final String k_pch_SteamVR_Section = "steamvr";
	final String k_pch_SteamVR_RenderTargetMultiplier_Float = "renderTargetMultiplier";
	
	static void onGuiScreenChanged(GuiScreen previousScreen, GuiScreen newScreen)
	{
		KeyBinding.unPressAllKeys();
		KeyboardSimulator.robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
		KeyboardSimulator.robot.mouseRelease(InputEvent.BUTTON2_DOWN_MASK);
		KeyboardSimulator.robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
		KeyboardSimulator.robot.keyRelease(KeyEvent.VK_SHIFT);
		
		if(newScreen == null 	|| (mc.player!=null && !mc.player.isEntityAlive())){
			guiPos_Room = null;
			guiRotationPose = null;
		}
		
		// main menu/win game/
		if (mc.world==null || newScreen instanceof GuiWinGame ) {
			//TODO reset scale things
			MCOpenVR.guiScale = 2.0f;
			mc.vrPlayer.worldScale = 1;
			mc.vrPlayer.interpolatedWorldScale = 1;
			mc.vrSettings.vrWorldRotationCached = mc.vrSettings.vrWorldRotation;
			mc.vrSettings.vrWorldRotation = 0;
			mc.vrPlayer.worldRotationRadians = (float) Math.toRadians( mc.vrSettings.vrWorldRotation);
			float[] playArea = getPlayAreaSize();
			guiPos_Room = new Vector3f(
					(float) (0),
					(float) (1.3f),
					(float) (playArea != null ? -playArea[1] / 2 : -1.5f) - 0.3f);			
			
			guiRotationPose = new Matrix4f();
			guiRotationPose.M[0][0] = guiRotationPose.M[1][1] = guiRotationPose.M[2][2] = guiRotationPose.M[3][3] = 1.0F;
			guiRotationPose.M[0][1] = guiRotationPose.M[1][0] = guiRotationPose.M[2][3] = guiRotationPose.M[3][1] = 0.0F;
			guiRotationPose.M[0][2] = guiRotationPose.M[1][2] = guiRotationPose.M[2][0] = guiRotationPose.M[3][2] = 0.0F;
			guiRotationPose.M[0][3] = guiRotationPose.M[1][3] = guiRotationPose.M[2][1] = guiRotationPose.M[3][0] = 0.0F;
			
			return;
		} else { //these dont update when screen open.
			if (mc.vrSettings.vrWorldRotationCached != 0) {
				mc.vrSettings.vrWorldRotation = mc.vrSettings.vrWorldRotationCached;
				mc.vrSettings.vrWorldRotationCached = 0;
			}
		}		
		
		// i am dead view
		if (mc.player!=null && !mc.player.isEntityAlive())
		{
			Matrix4f rot = Matrix4f.rotationY((float) Math.toRadians(mc.vrSettings.vrWorldRotation));
			Matrix4f max = Matrix4f.multiply(rot, MCOpenVR.hmdRotation);
			MCOpenVR.guiScale = 1.0f*mc.vrPlayer.worldScale;
			Vec3d v = mc.entityRenderer.getEyeRenderPos(renderPass.Center);
			Vec3d d = mc.roomScale.getHMDDir_World();
			Vector3f guiPos_World = new Vector3f(
					(float) (v.xCoord + d.xCoord*mc.vrPlayer.worldScale),
					(float) (v.yCoord + d.yCoord*mc.vrPlayer.worldScale),
					(float) (v.zCoord + d.zCoord*mc.vrPlayer.worldScale));

			Quatf orientationQuat = OpenVRUtil.convertMatrix4ftoRotationQuat(max);

			guiRotationPose = new Matrix4f(orientationQuat);

			guiRotationPose.M[3][3] = 1.0f;
			
			guiPos_Room = guiPos_World.subtract(new Vector3f((float)mc.entityRenderer.interPolatedRoomOrigin.xCoord,
					(float) mc.entityRenderer.interPolatedRoomOrigin.yCoord, (float) mc.entityRenderer.interPolatedRoomOrigin.zCoord));
		
		}  else if ( previousScreen==null && newScreen != null	|| 
				newScreen instanceof GuiContainerCreative 
				|| newScreen instanceof GuiChat) {			

			Quatf controllerOrientationQuat;
			boolean appearOverBlock = (newScreen instanceof GuiCrafting)
					|| (newScreen instanceof GuiChest)
					|| (newScreen instanceof GuiHopper)
					|| (newScreen instanceof GuiFurnace)
					|| (newScreen instanceof GuiBrewingStand)
					|| (newScreen instanceof GuiBeacon)
					|| (newScreen instanceof GuiDispenser)
					|| (newScreen instanceof GuiEnchantment)
					|| (newScreen instanceof GuiRepair)
					;

			if(appearOverBlock && mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK){	
				
				Vector3f guiPos_World =new Vector3f((float) mc.objectMouseOver.getBlockPos().getX() + 0.5f,
						(float) mc.objectMouseOver.getBlockPos().getY() + 1.7f,
						(float) mc.objectMouseOver.getBlockPos().getZ() + 0.5f);
				
				Vec3d pos = mc.roomScale.getHMDPos_World();
				guiScale =(float) (Math.sqrt(mc.vrPlayer.worldScale) * (pos.distanceTo(new Vec3d(guiPos_World.x, guiPos_World.y, guiPos_World.z)) / 2) * 2);

				Vector3f look = new Vector3f();
				look.x = (float) (guiPos_World.x - pos.xCoord);
				look.y = (float) (guiPos_World.y - pos.yCoord);
				look.z = (float) (guiPos_World.z - pos.zCoord);

				float pitch = (float) Math.asin(look.y/look.length());
				float yaw = (float) ((float) Math.PI + Math.atan2(look.x, look.z));    
				guiRotationPose = Matrix4f.rotationY((float) yaw);
				Matrix4f tilt = OpenVRUtil.rotationXMatrix(pitch);	
				guiRotationPose = Matrix4f.multiply(guiRotationPose,tilt);		
				
				guiPos_Room = guiPos_World.subtract(new Vector3f((float)mc.entityRenderer.interPolatedRoomOrigin.xCoord,
						(float) mc.entityRenderer.interPolatedRoomOrigin.yCoord, (float) mc.entityRenderer.interPolatedRoomOrigin.zCoord));
			
			}				
			else{
				Vec3d adj = new Vec3d(0,0,-2);
				if (newScreen instanceof GuiChat){
					 adj = new Vec3d(0.3,1,-2);
				} else if (newScreen instanceof GuiScreenBook || newScreen instanceof GuiEditSign) {
					 adj = new Vec3d(0,1,-2);
				}
				
				Vec3d v = mc.vrPlayer.getHMDPos_World();
				Vec3d e = mc.roomScale.getCustomHMDVector(adj);
				Vector3f guiPos_World = new Vector3f(
						(float) (e.xCoord * mc.vrPlayer.worldScale / 2 + v.xCoord),
						(float) (e.yCoord* mc.vrPlayer.worldScale / 2 + v.yCoord),
						(float) (e.zCoord* mc.vrPlayer.worldScale / 2 + v.zCoord));
				Matrix4f hmd = hmdRotation;
				Matrix4f rot = Matrix4f.rotationY((float) Math.toRadians(mc.vrSettings.vrWorldRotation));
				hmd = Matrix4f.multiply(hmd, rot);

				guiScale = mc.vrPlayer.worldScale;
				if(mc.world == null) guiScale = 2.0f;
							
				guiRotationPose = Matrix4f.rotationY((float) Math.toRadians( getHeadYawDegrees() + mc.vrSettings.vrWorldRotation));
				Matrix4f tilt = OpenVRUtil.rotationXMatrix((float)Math.toRadians(mc.roomScale.getHMDPitch_World()));	
				guiRotationPose = Matrix4f.multiply(guiRotationPose,tilt);
				
				guiPos_Room= guiPos_World.subtract(new Vector3f((float)mc.entityRenderer.interPolatedRoomOrigin.xCoord,
						(float) mc.entityRenderer.interPolatedRoomOrigin.yCoord, (float) mc.entityRenderer.interPolatedRoomOrigin.zCoord));
			
			}
		}

	
	}

	//-------------------------------------------------------
	// IBodyAimController

	float getBodyPitchDegrees() {
		return 0; //Always return 0 for body pitch
	}
	
	float getAimYaw() {
		return aimYaw;
	}
	
	float getAimPitch() {
		return aimPitch;
	}
	
    Vector3f forward = new Vector3f(0,0,-1);
	
	Vec3d getAimVector( int controller ) {
		Matrix4f aimRotation = controllerRotation[controller];
        Vector3f controllerDirection = aimRotation.transform(forward);
		Vec3d out = new Vec3d(controllerDirection.x, controllerDirection.y,controllerDirection.z);
		return out;

	}
	
	
	public static Matrix4f getAimRotation( int controller ) {
		return controllerRotation[controller];
	}
	
	public static Matrix4f getHandRotation( int controller ) {
		return handRotation[controller];
	}
	
	
	public boolean initBodyAim() throws Exception
	{
		return init();
	}


	public static Vec3d getAimSource( int controller ) {
		Vec3d out = new Vec3d(aimSource[controller].xCoord, aimSource[controller].yCoord, aimSource[controller].zCoord);
		if(!mc.vrSettings.seated) out.addVector((double)offset.x, (double)offset.y,(double) offset.z);
		return out;
	}
	
	public static void triggerHapticPulse(int controller, int strength) {
		if (controllerDeviceIndex[controller]==-1)
			return;
		vrsystem.TriggerHapticPulse.apply(controllerDeviceIndex[controller], 0, (short)strength);
	}

	private static void updateAim() {
		if (mc==null)
			return;
		Vector3f forward = new Vector3f(0,0,-1);
		
		{//hmd
			hmdRotation.M[0][0] = hmdPose.M[0][0];
			hmdRotation.M[0][1] = hmdPose.M[0][1];
			hmdRotation.M[0][2] = hmdPose.M[0][2];
			hmdRotation.M[0][3] = 0.0F;
			hmdRotation.M[1][0] = hmdPose.M[1][0];
			hmdRotation.M[1][1] = hmdPose.M[1][1];
			hmdRotation.M[1][2] = hmdPose.M[1][2];
			hmdRotation.M[1][3] = 0.0F;
			hmdRotation.M[2][0] = hmdPose.M[2][0];
			hmdRotation.M[2][1] = hmdPose.M[2][1];
			hmdRotation.M[2][2] = hmdPose.M[2][2];
			hmdRotation.M[2][3] = 0.0F;
			hmdRotation.M[3][0] = 0.0F;
			hmdRotation.M[3][1] = 0.0F;
			hmdRotation.M[3][2] = 0.0F;
			hmdRotation.M[3][3] = 1.0F;

			headDirection = hmdRotation.transform(forward);

			Vec3d eye = getCenterEyePosition();
			hmdHistory.add(eye);
			Vector3f v3 = MCOpenVR.hmdRotation.transform(new Vector3f(0,-.1f, .1f));
			hmdPivotHistory.add(new Vec3d(v3.x+eye.xCoord, v3.y+eye.yCoord, v3.z+eye.zCoord));
			
			haimPitch = (float)Math.toDegrees(Math.asin(headDirection.y/headDirection.length()));
			haimYaw = (float)Math.toDegrees(Math.atan2(headDirection.x, headDirection.z));

		}
		
		{//right controller
			handRotation[0].M[0][0] = controllerPose[0].M[0][0];
			handRotation[0].M[0][1] = controllerPose[0].M[0][1];
			handRotation[0].M[0][2] = controllerPose[0].M[0][2];
			handRotation[0].M[0][3] = 0.0F;
			handRotation[0].M[1][0] = controllerPose[0].M[1][0];
			handRotation[0].M[1][1] = controllerPose[0].M[1][1];
			handRotation[0].M[1][2] = controllerPose[0].M[1][2];
			handRotation[0].M[1][3] = 0.0F;
			handRotation[0].M[2][0] = controllerPose[0].M[2][0];
			handRotation[0].M[2][1] = controllerPose[0].M[2][1];
			handRotation[0].M[2][2] = controllerPose[0].M[2][2];
			handRotation[0].M[2][3] = 0.0F;
			handRotation[0].M[3][0] = 0.0F;
			handRotation[0].M[3][1] = 0.0F;
			handRotation[0].M[3][2] = 0.0F;
			handRotation[0].M[3][3] = 1.0F;	

			if(mc.vrSettings.seated){
				controllerPose[0] = hmdPose.inverted().inverted();
				controllerPose[1] = hmdPose.inverted().inverted();
			} else	
				controllerPose[0] = Matrix4f.multiply(controllerPose[0], controllerTipTransform[0]);

			// grab controller position in tracker space, scaled to minecraft units
			Vector3f controllerPos = OpenVRUtil.convertMatrix4ftoTranslationVector(controllerPose[0]);
			aimSource[0] = new Vec3d(
					controllerPos.x,
					controllerPos.y,
					controllerPos.z);

			controllerHistory[0].add(aimSource[0]);

			// build matrix describing controller rotation
			controllerRotation[0].M[0][0] = controllerPose[0].M[0][0];
			controllerRotation[0].M[0][1] = controllerPose[0].M[0][1];
			controllerRotation[0].M[0][2] = controllerPose[0].M[0][2];
			controllerRotation[0].M[0][3] = 0.0F;
			controllerRotation[0].M[1][0] = controllerPose[0].M[1][0];
			controllerRotation[0].M[1][1] = controllerPose[0].M[1][1];
			controllerRotation[0].M[1][2] = controllerPose[0].M[1][2];
			controllerRotation[0].M[1][3] = 0.0F;
			controllerRotation[0].M[2][0] = controllerPose[0].M[2][0];
			controllerRotation[0].M[2][1] = controllerPose[0].M[2][1];
			controllerRotation[0].M[2][2] = controllerPose[0].M[2][2];
			controllerRotation[0].M[2][3] = 0.0F;
			controllerRotation[0].M[3][0] = 0.0F;
			controllerRotation[0].M[3][1] = 0.0F;
			controllerRotation[0].M[3][2] = 0.0F;
			controllerRotation[0].M[3][3] = 1.0F;

			if(mc.vrSettings.seated && mc.currentScreen == null){
				org.lwjgl.util.vector.Matrix4f temp = new org.lwjgl.util.vector.Matrix4f();

				float hRange = 110;
				float vRange = 180;
				double h = Mouse.getX() / (double) mc.displayWidth * hRange - (hRange / 2);
				double v = Mouse.getY() / (double) mc.displayHeight * vRange - (vRange / 2);

				double nPitch=-v;

//				float con = mc.vrPlayer.getControllerYaw_World(0);
//				float hmd = mc.vrPlayer.getHMDYaw_World();
//				
//				float diff = con - hmd;
//				if(diff > 180 ) diff -= 360;
//				if(diff < -180 ) diff += 360;
					
				
				if(Display.isActive()){
					float rotStart = mc.vrSettings.keyholeX;
					float rotSpeed = 2000 * mc.vrSettings.xSensitivity;
					int leftedge=(int)((-rotStart + (hRange / 2)) *(double) mc.displayWidth / hRange )+1;
					int rightedge=(int)((rotStart + (hRange / 2)) *(double) mc.displayWidth / hRange )-1;
					float rotMul = ((float)Math.abs(h) - rotStart) / ((hRange / 2) - rotStart); // Scaled 0...1 from rotStart to FOV edge
					if(rotMul > 0.15) rotMul = 0.15f;

					if(h < -rotStart){
						mc.vrSettings.vrWorldRotation += rotSpeed * rotMul * mc.getFrameDelta();
						mc.vrSettings.vrWorldRotation %= 360; // Prevent stupidly large values
						hmdForwardYaw = (float)Math.toDegrees(Math.atan2(headDirection.x, headDirection.z));    
						Mouse.setCursorPosition(leftedge,Mouse.getY());
						h=-rotStart;
					}
					if(h > rotStart){
						mc.vrSettings.vrWorldRotation -= rotSpeed * rotMul * mc.getFrameDelta();
						mc.vrSettings.vrWorldRotation %= 360; // Prevent stupidly large values
						hmdForwardYaw = (float)Math.toDegrees(Math.atan2(headDirection.x, headDirection.z));    
						Mouse.setCursorPosition(rightedge,Mouse.getY());
						h=rotStart;
					}

					double ySpeed=0.5 * mc.vrSettings.ySensitivity;
					nPitch=aimPitch+(v)*ySpeed;
					nPitch=MathHelper.clamp(nPitch,-89.9,89.9);
					Mouse.setCursorPosition(Mouse.getX(),mc.displayHeight/2);

				}
				temp.rotate((float) Math.toRadians(-nPitch), new org.lwjgl.util.vector.Vector3f(1,0,0));

				temp.rotate((float) Math.toRadians(-180 + h - hmdForwardYaw), new org.lwjgl.util.vector.Vector3f(0,1,0));

				controllerRotation[0].M[0][0] = temp.m00;
				controllerRotation[0].M[0][1] = temp.m01;
				controllerRotation[0].M[0][2] = temp.m02;

				controllerRotation[0].M[1][0] = temp.m10;
				controllerRotation[0].M[1][1] = temp.m11;
				controllerRotation[0].M[1][2] = temp.m12;

				controllerRotation[0].M[2][0] = temp.m20;
				controllerRotation[0].M[2][1] = temp.m21;
				controllerRotation[0].M[2][2] = temp.m22;
			}

			// Calculate aim angles from controller orientation
			// Minecraft entities don't have a roll, so just base it on a direction
			controllerDirection = controllerRotation[0].transform(forward);
			aimPitch = (float)Math.toDegrees(Math.asin(controllerDirection.y/controllerDirection.length()));
			aimYaw = (float)Math.toDegrees(Math.atan2(controllerDirection.x, controllerDirection.z));
		}
		
		{//left controller
			handRotation[1].M[0][0] = controllerPose[1].M[0][0];
			handRotation[1].M[0][1] = controllerPose[1].M[0][1];
			handRotation[1].M[0][2] = controllerPose[1].M[0][2];
			handRotation[1].M[0][3] = 0.0F;
			handRotation[1].M[1][0] = controllerPose[1].M[1][0];
			handRotation[1].M[1][1] = controllerPose[1].M[1][1];
			handRotation[1].M[1][2] = controllerPose[1].M[1][2];
			handRotation[1].M[1][3] = 0.0F;
			handRotation[1].M[2][0] = controllerPose[1].M[2][0];
			handRotation[1].M[2][1] = controllerPose[1].M[2][1];
			handRotation[1].M[2][2] = controllerPose[1].M[2][2];
			handRotation[1].M[2][3] = 0.0F;
			handRotation[1].M[3][0] = 0.0F;
			handRotation[1].M[3][1] = 0.0F;
			handRotation[1].M[3][2] = 0.0F;
			handRotation[1].M[3][3] = 1.0F;	

			// update off hand aim
			if(!mc.vrSettings.seated) 
				controllerPose[1] = Matrix4f.multiply(controllerPose[1], controllerTipTransform[1]);

			Vector3f leftControllerPos = OpenVRUtil.convertMatrix4ftoTranslationVector(controllerPose[1]);
			aimSource[1] = new Vec3d(
					leftControllerPos.x,
					leftControllerPos.y,
					leftControllerPos.z);
			controllerHistory[1].add(aimSource[1]);

			// build matrix describing controller rotation
			controllerRotation[1].M[0][0] = controllerPose[1].M[0][0];
			controllerRotation[1].M[0][1] = controllerPose[1].M[0][1];
			controllerRotation[1].M[0][2] = controllerPose[1].M[0][2];
			controllerRotation[1].M[0][3] = 0.0F;
			controllerRotation[1].M[1][0] = controllerPose[1].M[1][0];
			controllerRotation[1].M[1][1] = controllerPose[1].M[1][1];
			controllerRotation[1].M[1][2] = controllerPose[1].M[1][2];
			controllerRotation[1].M[1][3] = 0.0F;
			controllerRotation[1].M[2][0] = controllerPose[1].M[2][0];
			controllerRotation[1].M[2][1] = controllerPose[1].M[2][1];
			controllerRotation[1].M[2][2] = controllerPose[1].M[2][2];
			controllerRotation[1].M[2][3] = 0.0F;
			controllerRotation[1].M[3][0] = 0.0F;
			controllerRotation[1].M[3][1] = 0.0F;
			controllerRotation[1].M[3][2] = 0.0F;
			controllerRotation[1].M[3][3] = 1.0F;

			if(mc.vrSettings.seated){
				aimSource[1] = getCenterEyePosition();
				aimSource[0] = getCenterEyePosition();
			}

			lcontrollerDirection = controllerRotation[1].transform(forward);
			laimPitch = (float)Math.toDegrees(Math.asin(lcontrollerDirection.y/lcontrollerDirection.length()));
			laimYaw = (float)Math.toDegrees(Math.atan2(lcontrollerDirection.x, lcontrollerDirection.z));
		}
		
		boolean debugThirdController = false;
		if(controllerTracking[2] || debugThirdController){ //third controller
			if(debugThirdController) controllerPose[2] = controllerPose[0];
			Vector3f thirdControllerPos = OpenVRUtil.convertMatrix4ftoTranslationVector(controllerPose[2]);
			aimSource[2] = new Vec3d(
					thirdControllerPos.x,
					thirdControllerPos.y,
					thirdControllerPos.z);
		
			// build matrix describing controller rotation
			controllerRotation[2].M[0][0] = controllerPose[2].M[0][0];
			controllerRotation[2].M[0][1] = controllerPose[2].M[0][1];
			controllerRotation[2].M[0][2] = controllerPose[2].M[0][2];
			controllerRotation[2].M[0][3] = 0.0F;
			controllerRotation[2].M[1][0] = controllerPose[2].M[1][0];
			controllerRotation[2].M[1][1] = controllerPose[2].M[1][1];
			controllerRotation[2].M[1][2] = controllerPose[2].M[1][2];
			controllerRotation[2].M[1][3] = 0.0F;
			controllerRotation[2].M[2][0] = controllerPose[2].M[2][0];
			controllerRotation[2].M[2][1] = controllerPose[2].M[2][1];
			controllerRotation[2].M[2][2] = controllerPose[2].M[2][2];
			controllerRotation[2].M[2][3] = 0.0F;
			controllerRotation[2].M[3][0] = 0.0F;
			controllerRotation[2].M[3][1] = 0.0F;
			controllerRotation[2].M[3][2] = 0.0F;
			controllerRotation[2].M[3][3] = 1.0F;

			thirdcontrollerDirection = controllerRotation[2].transform(forward);

		}
		
		if(controllerDeviceIndex[THIRD_CONTROLLER]!=-1 && mc.vrSettings.displayMirrorMode == VRSettings.MIRROR_MIXED_REALITY || debugThirdController) {
			VRHotkeys.snapMRCam(mc, debugThirdController ? 0 : 2);
			mrMovingCamActive = true;
		} else {
			mrMovingCamActive = false;
		}
				
		
	}

	public static void debugOutput(){

	}

	public static double getCurrentTimeSecs()
	{
		return System.nanoTime() / 1000000000d;
	}

	public static void resetPosition() {
		Vec3d pos= getCenterEyePosition().scale(-1).addVector(offset.x,offset.y,offset.z);
		offset=new Vector3f((float) pos.xCoord,(float)pos.yCoord+1.62f,(float)pos.zCoord);
	}
	
	public static void clearOffset() {
		offset=new Vector3f(0,0,0);
	}

    public static boolean isVivecraftBinding(KeyBinding kb) {
    	return kb == hotbarNext || kb == hotbarPrev || kb == rotateLeft || kb == rotateRight || kb == walkabout || kb == rotateFree || kb == quickTorch;
    }
}
