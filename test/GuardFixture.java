package com.fiesta.guardfixture;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.inventory.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import fr.xephi.authme.api.v3.AuthMeApi;
public final class GuardFixture extends JavaPlugin implements Listener {
 private final Set<UUID> authenticated=new HashSet<>();private boolean started=false;
 private void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);getLogger().info("GUARD_FIXTURE_READY");}
 @EventHandler public void join(PlayerJoinEvent e){Player p=e.getPlayer();Bukkit.getScheduler().runTaskLater(this,()->{
  try{AuthMeApi.getInstance().forceRegister(p,"FixturePass123!",true);Bukkit.getScheduler().runTaskLater(this,()->{
   if(!AuthMeApi.getInstance().isAuthenticated(p))AuthMeApi.getInstance().forceLogin(p);
   Bukkit.getScheduler().runTaskLater(this,()->{
    if(AuthMeApi.getInstance().isAuthenticated(p))authenticated.add(p.getUniqueId());
    if(!started&&authenticated.size()==3){started=true;Bukkit.getScheduler().runTaskLater(this,this::run,40L);}
   },40L);
  },80L);}catch(Throwable ex){fail(ex);}
 },20L);}
 private boolean breaking(Player p,Location l){BlockBreakEvent e=new BlockBreakEvent(l.getBlock(),p);Bukkit.getPluginManager().callEvent(e);return e.isCancelled();}
 private int headSlot(Player p,String name){for(int i=0;i<45;i++){ItemStack item=p.getOpenInventory().getTopInventory().getItem(i);if(item!=null&&item.getType()==Material.PLAYER_HEAD&&item.getItemMeta().getDisplayName().contains(name))return i;}throw new AssertionError("Missing menu entry for "+name);}
 private void click(Player p,int slot){InventoryClickEvent e=new InventoryClickEvent(p.getOpenInventory(),InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL);Bukkit.getPluginManager().callEvent(e);check(e.isCancelled(),"GUI click cancelled");}
 private void run(){try{
  Player b=Bukkit.getPlayerExact("GuardBuilder"),f=Bukkit.getPlayerExact("GuardFriend"),v=Bukkit.getPlayerExact("GuardVisitor");
  check(b!=null&&f!=null&&v!=null,"fixture players online");World w=Bukkit.getWorld("world");Location in=new Location(w,16,70,16),out=new Location(w,1024,70,1024);
  check(breaking(b,in)&&breaking(f,in)&&breaking(v,in),"ordinary players blocked inside");
  check(!breaking(v,out),"outside protected area unaffected");
  PlayerInteractEvent interact=new PlayerInteractEvent(v,Action.RIGHT_CLICK_BLOCK,null,in.getBlock(),org.bukkit.block.BlockFace.UP);
  in.getBlock().setType(Material.CHEST);Bukkit.getPluginManager().callEvent(interact);
  check(interact.useInteractedBlock()==Event.Result.DENY,"visitor cannot open protected chest");
  in.getBlock().setType(Material.OAK_TRAPDOOR);PlayerInteractEvent door=new PlayerInteractEvent(v,Action.RIGHT_CLICK_BLOCK,new ItemStack(Material.BREAD),in.getBlock(),org.bukkit.block.BlockFace.UP);Bukkit.getPluginManager().callEvent(door);
  check(door.useInteractedBlock()!=Event.Result.DENY&&door.useItemInHand()!=Event.Result.DENY,"visitor can use trapdoor and eat");
  PlayerInteractEvent hoe=new PlayerInteractEvent(v,Action.RIGHT_CLICK_BLOCK,new ItemStack(Material.IRON_HOE),in.getBlock(),org.bukkit.block.BlockFace.UP);Bukkit.getPluginManager().callEvent(hoe);check(hoe.useItemInHand()==Event.Result.DENY,"visitor cannot modify protected block with hoe");
  InventoryClickEvent personal=new InventoryClickEvent(v.getOpenInventory(),InventoryType.SlotType.CONTAINER,0,ClickType.LEFT,InventoryAction.PICKUP_ALL);Bukkit.getPluginManager().callEvent(personal);check(!personal.isCancelled(),"visitor can use personal inventory");
  Entity zombie=w.spawnEntity(in,EntityType.ZOMBIE),cow=w.spawnEntity(in,EntityType.COW);
  EntityDamageByEntityEvent hostile=new EntityDamageByEntityEvent(v,zombie,DamageCause.ENTITY_ATTACK,1.0);Bukkit.getPluginManager().callEvent(hostile);check(!hostile.isCancelled(),"visitor can fight hostile mobs");
  EntityDamageByEntityEvent passive=new EntityDamageByEntityEvent(v,cow,DamageCause.ENTITY_ATTACK,1.0);Bukkit.getPluginManager().callEvent(passive);check(passive.isCancelled(),"visitor cannot damage protected animals");zombie.remove();cow.remove();
  java.util.List<org.bukkit.block.Block> blast=new java.util.ArrayList<>();blast.add(in.getBlock());blast.add(out.getBlock());
  BlockExplodeEvent explode=new BlockExplodeEvent(out.getBlock(),out.getBlock().getState(),blast,0.0f,org.bukkit.ExplosionResult.DESTROY);
  Bukkit.getPluginManager().callEvent(explode);check(!explode.blockList().contains(in.getBlock())&&explode.blockList().contains(out.getBlock()),"explosion protects only spawn blocks");
  org.bukkit.block.Block outside=w.getBlockAt(528,70,0),boundary=w.getBlockAt(527,70,0);
  BlockPistonExtendEvent piston=new BlockPistonExtendEvent(outside,java.util.List.of(boundary),org.bukkit.block.BlockFace.EAST);
  Bukkit.getPluginManager().callEvent(piston);check(piston.isCancelled(),"outside piston cannot move protected block");
  Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"trust GuardBuilder builder");
  Bukkit.getScheduler().runTaskLater(this,()->afterGrant(b,f,v,in,out),80L);
 }catch(Throwable t){fail(t);}}
 private void afterGrant(Player b,Player f,Player v,Location in,Location out){try{
  check(b.hasPermission("fiesta.tag.builder"),"builder permission set via LuckPerms");
  check(!breaking(b,in),"builder allowed inside");
  check(breaking(f,in),"friend blocked before trust");
  Bukkit.dispatchCommand(b,"trust GuardFriend player");
  Bukkit.getScheduler().runTaskLater(this,()->afterTrust(b,f,v,in),80L);
 }catch(Throwable t){fail(t);}}
 private void afterTrust(Player b,Player f,Player v,Location in){try{
  check(!breaking(f,in),"trusted friend allowed");check(breaking(v,in),"untrusted visitor blocked");
  Bukkit.dispatchCommand(f,"trust");check(f.getOpenInventory().getTopInventory().getHolder()==null||!f.getOpenInventory().getTopInventory().getHolder().getClass().getName().contains("TrustMenu"),"trusted friend cannot open management menu");
  Bukkit.dispatchCommand(b,"trust");check(b.getOpenInventory().getTopInventory().getHolder().getClass().getName().contains("TrustMenu"),"builder can open trust menu");
  check(b.getOpenInventory().getTopInventory().getItem(0).getType()==Material.PLAYER_HEAD,"trusted player visible in menu");
  InventoryClickEvent select=new InventoryClickEvent(b.getOpenInventory(),InventoryType.SlotType.CONTAINER,0,ClickType.LEFT,InventoryAction.PICKUP_ALL);Bukkit.getPluginManager().callEvent(select);check(select.isCancelled(),"menu items cannot be taken");
  Bukkit.getScheduler().runTaskLater(this,()->guiConfirmCancel(b,f,v,in),5L);
 }catch(Throwable t){fail(t);}}
 private void guiConfirmCancel(Player b,Player f,Player v,Location in){try{
  check(b.getOpenInventory().getTopInventory().getItem(22).getType()==Material.RED_CONCRETE,"removal requires confirmation");
  InventoryClickEvent cancel=new InventoryClickEvent(b.getOpenInventory(),InventoryType.SlotType.CONTAINER,49,ClickType.LEFT,InventoryAction.PICKUP_ALL);Bukkit.getPluginManager().callEvent(cancel);check(!breaking(f,in),"cancel leaves trust intact");
  Bukkit.getScheduler().runTaskLater(this,()->guiAddMenu(b,f,v,in),5L);
 }catch(Throwable t){fail(t);}}
 private void guiAddMenu(Player b,Player f,Player v,Location in){try{
  InventoryClickEvent addMenu=new InventoryClickEvent(b.getOpenInventory(),InventoryType.SlotType.CONTAINER,49,ClickType.LEFT,InventoryAction.PICKUP_ALL);Bukkit.getPluginManager().callEvent(addMenu);
  Bukkit.getScheduler().runTaskLater(this,()->guiAddClick(b,f,v,in),5L);
 }catch(Throwable t){fail(t);}}
 private void guiAddClick(Player b,Player f,Player v,Location in){try{
  click(b,headSlot(b,"GuardVisitor"));
  Bukkit.getScheduler().runTaskLater(this,()->afterGuiAdd(b,f,v,in),50L);
 }catch(Throwable t){fail(t);}}
 private void afterGuiAdd(Player b,Player f,Player v,Location in){try{
  check(!breaking(v,in),"GUI added online helper");
  InventoryClickEvent select=new InventoryClickEvent(b.getOpenInventory(),InventoryType.SlotType.CONTAINER,1,ClickType.LEFT,InventoryAction.PICKUP_ALL);Bukkit.getPluginManager().callEvent(select);
  Bukkit.getScheduler().runTaskLater(this,()->guiRemoveConfirm(b,f,v,in),5L);
 }catch(Throwable t){fail(t);}}
 private void guiRemoveConfirm(Player b,Player f,Player v,Location in){try{
  InventoryClickEvent confirm=new InventoryClickEvent(b.getOpenInventory(),InventoryType.SlotType.CONTAINER,22,ClickType.LEFT,InventoryAction.PICKUP_ALL);Bukkit.getPluginManager().callEvent(confirm);
  Bukkit.getScheduler().runTaskLater(this,()->afterGuiCleanup(b,f,v,in),50L);
 }catch(Throwable t){fail(t);}}
 private void afterGuiCleanup(Player b,Player f,Player v,Location in){try{
  check(breaking(v,in),"GUI confirmation removed helper");
  Bukkit.dispatchCommand(b,"trust GuardVisitor builder");
  Bukkit.dispatchCommand(f,"trust GuardVisitor builder");
  Bukkit.dispatchCommand(f,"trust GuardVisitor player");
  Bukkit.getScheduler().runTaskLater(this,()->afterDelegation(b,f,v,in),50L);
 }catch(Throwable t){fail(t);}}
 private void afterDelegation(Player b,Player f,Player v,Location in){try{
  check(breaking(v,in),"trusted friend cannot delegate");check(!v.hasPermission("fiesta.tag.builder"),"trusted friend cannot grant builder tag");
  b.kickPlayer("Fixture: test offline owner");
  Bukkit.getScheduler().runTaskLater(this,()->{try{
   check(!breaking(f,in),"trust remains active with builder offline");
   Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"trust remove GuardFriend player GuardBuilder");
   Bukkit.getScheduler().runTaskLater(this,()->opDefault(f,v,in),60L);
  }catch(Throwable t){fail(t);}},60L);
 }catch(Throwable t){fail(t);}}
 private void opDefault(Player p,Player v,Location in){try{
  check(breaking(p,in),"admin revoked offline builder trust");p.setOp(true);
  Bukkit.getScheduler().runTaskLater(this,()->{try{
   check(p.hasPermission("fiesta.tag.builder")&&!breaking(p,in),"operator receives builder by default");
   Bukkit.dispatchCommand(p,"trust remove GuardFriend builder");
   Bukkit.getScheduler().runTaskLater(this,()->opRevoked(p,v,in),50L);
  }catch(Throwable t){fail(t);}},30L);
 }catch(Throwable t){fail(t);}}
 private void opRevoked(Player p,Player v,Location in){try{
  check(!p.hasPermission("fiesta.tag.builder")&&breaking(p,in),"operator can remove own builder tag");
  Bukkit.dispatchCommand(p,"trust GuardFriend builder");
  Bukkit.getScheduler().runTaskLater(this,()->{try{
   check(p.hasPermission("fiesta.tag.builder")&&!breaking(p,in),"operator can restore own builder tag");
   Bukkit.dispatchCommand(p,"trust");click(p,49);
   Bukkit.getScheduler().runTaskLater(this,()->{try{
    click(p,headSlot(p,"GuardFriend"));
    Bukkit.getScheduler().runTaskLater(this,()->selfAdded(p,v,in),40L);
   }catch(Throwable t){fail(t);}},5L);
  }catch(Throwable t){fail(t);}},50L);
 }catch(Throwable t){fail(t);}}
 private void selfAdded(Player p,Player v,Location in){try{
  check(headSlot(p,"GuardFriend")>=0,"operator can add self to GUI as helper");click(p,headSlot(p,"GuardFriend"));
  Bukkit.getScheduler().runTaskLater(this,()->{try{click(p,22);Bukkit.getScheduler().runTaskLater(this,()->{try{
   check(p.getOpenInventory().getTopInventory().getItem(22).getType()==Material.PAPER,"operator can remove self from GUI helper list");
   Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"lp user GuardFriend permission unset fiesta.tag.builder");
   Bukkit.getScheduler().runTaskLater(this,()->{try{
    check(p.hasPermission("fiesta.tag.builder"),"operator default restored without explicit node");Bukkit.dispatchCommand(p,"trust GuardVisitor player");
   Bukkit.getScheduler().runTaskLater(this,()->{try{
    check(!breaking(v,in),"helper trusted by operator builder");p.kickPlayer("Fixture: offline operator");
    Bukkit.getScheduler().runTaskLater(this,()->{try{
     check(!breaking(v,in),"operator helper access persists while owner offline");
     Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"trust remove GuardFriend builder");
     Bukkit.getScheduler().runTaskLater(this,()->{try{check(breaking(v,in),"offline operator revocation removes helper access");getLogger().info("GUARD_FIXTURE_PASS");Bukkit.shutdown();}catch(Throwable t){fail(t);}},50L);
    }catch(Throwable t){fail(t);}},40L);
   }catch(Throwable t){fail(t);}},50L);
   }catch(Throwable t){fail(t);}},40L);
  }catch(Throwable t){fail(t);}},40L);}catch(Throwable t){fail(t);}},5L);
 }catch(Throwable t){fail(t);}}
 private void fail(Throwable t){getLogger().severe("GUARD_FIXTURE_FAIL: "+t);t.printStackTrace();Bukkit.getScheduler().runTask(this,Bukkit::shutdown);}
}
