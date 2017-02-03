package com.mtbs3d.minecrift.gameplay;

import javax.swing.LayoutStyle;

import com.mtbs3d.minecrift.api.IRoomscaleAdapter;
import com.mtbs3d.minecrift.api.NetworkHelper;
import com.mtbs3d.minecrift.api.NetworkHelper.PacketDiscriminators;
import com.mtbs3d.minecrift.provider.MCOpenVR;
import com.mtbs3d.minecrift.render.PlayerModelController;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class ClimbTracker {

	private boolean[] latched = new boolean[2];
	private boolean[] wasinblock = new boolean[2];
	private boolean[] wasbutton= new boolean[2];

	private boolean gravityOverride=false;
	
	private Minecraft mc;

	public Vec3d[] latchStart = new Vec3d[]{new Vec3d(0,0,0), new Vec3d(0,0,0)};
	public double[] latchStartBodyY = new double[2];
	public int latchStartController = -1;
	boolean wantjump = false;
	AxisAlignedBB box[] = new AxisAlignedBB[2];
	public ClimbTracker(Minecraft minecraft) {
		this.mc = minecraft;
	}
	
	public boolean isGrabbingLadder(){
		return latched[0] || latched[1];
	}

	public boolean isActive(EntityPlayerSP p){
		if(mc.vrSettings.seated)
			return false;
		if(!mc.vrSettings.vrFreeMove && !Minecraft.getMinecraft().vrSettings.simulateFalling)
			return false;
		if(!mc.vrSettings.realisticClimbEnabled)
			return false;
		if(p==null || p.isDead)
			return false;
		if(p.isRiding())
			return false;
		if(!isClimbeyClimbEquipped() && p.moveForward > 0 && Minecraft.getMinecraft().vrSettings.vrFreeMove ) 
			return false;
		return true;
	}
	   
    public boolean isClimbeyClimb(){
    	if(!this.isActive(mc.player)) return false;
    	return(isClimbeyClimbEquipped());
    }
    
    public boolean isClimbeyClimbEquipped(){
    	return(NetworkHelper.serverAllowsClimbey && mc.player.isClimbeyClimbEquipped());
    }
    
	private boolean canstand(BlockPos bp, EntityPlayerSP p){
		AxisAlignedBB t = p.world.getBlockState(bp).getCollisionBoundingBox(p.world, bp);
		if(t == null || t.maxY == 0)
			return false;	
		BlockPos bp1 = bp.up();	
		AxisAlignedBB a = p.world.getBlockState(bp1).getCollisionBoundingBox(p.world, bp1);
		if(a != null && a.maxY>0)
			return false;
		BlockPos bp2 = bp1.up();	
		AxisAlignedBB a1 = p.world.getBlockState(bp2).getCollisionBoundingBox(p.world, bp2);
		if(a1 != null && a1.maxY>0)
			return false;		
		return true;
	}

	public void doProcess(EntityPlayerSP player){
		if(!isActive(player)) {
			latchStartController = -1;
			latched[0] = false;
			latched[1] = false;
			player.setNoGravity(false);
			return;
		}

		IRoomscaleAdapter provider = mc.roomScale;

		boolean[] button = new boolean[2];
		boolean[] inblock = new boolean[2];

	
		boolean nope = false;
		
		boolean jump = false;
		
		for(int c=0;c<2;c++){
			Vec3d controllerPos=mc.roomScale.getControllerPos_World(c);
			BlockPos bp = new BlockPos(controllerPos);
			IBlockState bs = mc.world.getBlockState(bp);
			Block b = bs.getBlock();
			box[c] = bs.getCollisionBoundingBox(mc.world, bp);
			
			if(!mc.climbTracker.isClimbeyClimb()){	
				if(b == Blocks.LADDER || b ==Blocks.VINE){
					int meta = b.getMetaFromState(bs);
					Vec3d cpos = controllerPos.subtract(bp.getX(), bp.getY(), bp.getZ());
	
					if(meta == 2){
						inblock[c] = cpos.zCoord > .9 && (cpos.xCoord > .1 && cpos.xCoord < .9);
					} else if (meta == 3){
						inblock[c] = cpos.zCoord < .1 && (cpos.xCoord > .1 && cpos.xCoord < .9);
					} else if (meta == 4){
						inblock[c] = cpos.xCoord > .9 && (cpos.zCoord > .1 && cpos.zCoord < .9);
					} else if (meta == 5){
						inblock[c] = cpos.xCoord < .1 && (cpos.zCoord > .1 && cpos.zCoord < .9);
					}			
				} else {
					if(latchStart[c].subtract(controllerPos).lengthSquared() > 0.25) 
						inblock[c] = false;
				}
				button[c]=inblock[c];
			} else { //Climbey
				//TODO whitelist by block type
				
				if(c == 0)
					button[c] = mc.gameSettings.keyBindAttack.isKeyDown() || mc.gameSettings.keyBindUseItem.isKeyDown();
				else 
					button[c] = mc.gameSettings.keyBindForward.isKeyDown() && mc.player.movementInput.forwardKeyDown == false;

				inblock[c] = box[c] != null && box[c].offset(bp).isVecInside(controllerPos);				
			}						
		

			if(!button[c] && latched[c]){ //let go 
				if(!inblock[c])mc.vrPlayer.triggerHapticPulse(c, 200);
				latched[c] = false;
				jump = true;
				System.out.println("1" + c);
			} 

			if(!latched[c] && !nope){
				if((!wasinblock[c] && inblock[c] && button[c]) ||
						(!wasbutton[c] && button[c] && inblock[c])){
					wantjump = false;
					latchStart[c] = mc.roomScale.getControllerPos_World(c);
					latchStartBodyY[c] = player.posY;
					latchStartController = c;
					latched[c] = true;
					System.out.println("2" + c);
					if(c==0){
						latched[1] = false;
						nope = true;
					}
					else 
						latched[0] = false;
					mc.vrPlayer.triggerHapticPulse(c, 1000);
				}
			}		

			wasbutton[c] = button[c];
			wasinblock[c] = inblock[c];

		}
		
		if(!latched[0] && !latched[1]){ 
			//check in case they let go with one hand, and other hand should take over.
			for(int c=0;c<2;c++){
				if(inblock[c] && button[c]){
					latchStart[c] = mc.roomScale.getControllerPos_World(c);
					latchStartBodyY[c] = player.posY;
					latchStartController = c;
					latched[c] = true;
					wantjump = false;
					mc.vrPlayer.triggerHapticPulse(c, 1000);
				}
			}
		}		
		
		
		if(!wantjump) 
			wantjump = mc.gameSettings.keyBindJump.isKeyDown() && mc.jumpTracker.isClimbeyJumpEquipped();
		
		jump &= wantjump;
			
		if(latched[0] || latched[1] && !gravityOverride) {
			player.setNoGravity(true);
			gravityOverride=true;
		}
		
		if(!latched[0] && !latched[1] && gravityOverride){
			player.setNoGravity(false);
			gravityOverride=false;
		}

		if(!latched[0] && !latched[1] && !jump){
			latchStartController = -1;
			return; //fly u fools
		}

		Vec3d now = mc.roomScale.getControllerPos_World(latchStartController);
		Vec3d start = latchStart[latchStartController];
		
		Vec3d delta= now.subtract(start);
		
		if(wantjump) //bzzzzzz
			mc.vrPlayer.triggerHapticPulse(latchStartController, 200);
		
		if(!jump){
			player.motionY = -delta.yCoord;
			player.motionX = -delta.xCoord;
			player.motionZ = -delta.zCoord;
			BlockPos b = new BlockPos(latchStart[latchStartController]);
			double yheight = latchStart[latchStartController].subtract(b.getX(), b.getY(), b.getZ()).yCoord;
			if(!wantjump && box[latchStartController] != null && yheight > box[latchStartController].maxY*.8 && canstand(b, player)){		
				double hmd = mc.roomScale.getHMDPos_Room().yCoord;
				double con = mc.roomScale.getControllerPos_Room(latchStartController).yCoord;
				if(con <= hmd/2){
					double mot = con + (1 - (latchStart[latchStartController].yCoord - b.getY()));
					if(mot > 0) player.setPosition(player.posX, player.posY+mot, player.posZ);
				}
			}
			
			player.fallDistance = 0;
			if(mc.isIntegratedServerRunning()) //handle server falling.
				for (EntityPlayerMP p : mc.getIntegratedServer().getPlayerList().getPlayers()) {
					if(p.getEntityId() == mc.player.getEntityId())
						p.fallDistance = 0;
			} else {
				CPacketCustomPayload pack =	NetworkHelper.getVivecraftClientPacket(PacketDiscriminators.CLIMBING, new byte[]{});
				if(mc.getConnection() !=null)
					mc.getConnection().sendPacket(pack);
			}
			
		} else { //jump!
			wantjump = false;
			Vec3d pl = player.getPositionVector().subtract(delta);

			Vec3d m = MCOpenVR.controllerHistory[latchStartController].netMovement(0.3).scale(0.75f);
			
			if (player.isPotionActive(MobEffects.JUMP_BOOST))
				m=m.scale((player.getActivePotionEffect(MobEffects.JUMP_BOOST).getAmplifier() + 1.5));
			
			player.motionX=-m.xCoord;
			player.motionY=-m.yCoord;
			player.motionZ=-m.zCoord;

			player.lastTickPosX = pl.xCoord;
			player.lastTickPosY = pl.yCoord;
			player.lastTickPosZ = pl.zCoord;			
			pl = pl.addVector(player.motionX, player.motionY, player.motionZ);					
			player.setPosition(pl.xCoord, pl.yCoord, pl.zCoord);
			mc.vrPlayer.snapRoomOriginToPlayerEntity(player, false, false, 0);			
		}
	}
}
