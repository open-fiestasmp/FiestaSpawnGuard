package com.fiesta.spawnguard;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import fr.xephi.authme.api.v3.AuthMeApi;

public final class SpawnGuard extends JavaPlugin implements Listener {
 private LuckPerms luck; private String world; private int cx,cz,radius; private File trustFile;
 private final Map<UUID,Set<UUID>> grants=new HashMap<>();
 private final Map<UUID,User> loadedGrantors=new ConcurrentHashMap<>();
 private boolean ready=false;
 @Override public void onEnable(){
  try{
   saveDefaultConfig(); world=getConfig().getString("world","world");
   cx=Math.floorDiv(getConfig().getInt("center-x",0),16);cz=Math.floorDiv(getConfig().getInt("center-z",0),16);
   radius=getConfig().getInt("radius-chunks",32);
   if(radius<16||radius>64||Bukkit.getWorld(world)==null)throw new IllegalStateException("Invalid spawn protection configuration");
   luck=LuckPermsProvider.get();trustFile=new File(getDataFolder(),"trust.yml");loadTrust();
   for(UUID builder:grants.keySet())luck.getUserManager().loadUser(builder).thenAccept(user->loadedGrantors.put(builder,user));
   luck.getEventBus().subscribe(this,UserDataRecalculateEvent.class,e->loadedGrantors.put(e.getUser().getUniqueId(),e.getUser()));
   Bukkit.getPluginManager().registerEvents(this,this);ready=true;
   getLogger().info("Enabled spawn guard for "+world+" centered at chunk "+cx+","+cz+" radius "+radius+"; trusts="+grants.values().stream().mapToInt(Set::size).sum());
  }catch(Exception e){getLogger().severe("Spawn guard startup failed: "+e);Bukkit.getPluginManager().disablePlugin(this);}
 }
 private void loadTrust()throws IOException{
  if(!trustFile.exists())return;
  YamlConfiguration cfg=new YamlConfiguration();try{cfg.load(trustFile);}catch(Exception ex){throw new IOException("Cannot load trust file",ex);}
  for(String key:cfg.getKeys(false)){
   UUID owner=UUID.fromString(key);Set<UUID> targets=new HashSet<>();
   for(String value:cfg.getStringList(key))targets.add(UUID.fromString(value));
   grants.put(owner,targets);
  }
 }
 private void saveTrust()throws IOException{
  YamlConfiguration cfg=new YamlConfiguration();for(var e:grants.entrySet())cfg.set(e.getKey().toString(),e.getValue().stream().map(UUID::toString).sorted().toList());
  File tmp=new File(getDataFolder(),"trust.yml.tmp");cfg.save(tmp);
  try{Files.move(tmp.toPath(),trustFile.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp.toPath(),trustFile.toPath(),StandardCopyOption.REPLACE_EXISTING);}
 }
 private boolean inside(Location l){if(l==null||l.getWorld()==null||!world.equals(l.getWorld().getName()))return false;long dx=(l.getBlockX()>>4)-cx,dz=(l.getBlockZ()>>4)-cz;return dx*dx+dz*dz<=(long)radius*radius;}
 private boolean signedIn(Player p){try{return AuthMeApi.getInstance().isAuthenticated(p);}catch(Throwable e){return false;}}
 private boolean isBuilder(User u){return u!=null&&u.getCachedData().getPermissionData().checkPermission("fiesta.tag.builder").asBoolean();}
 private boolean allowed(Player p){
  if(!ready||!signedIn(p))return false;
  if(p.hasPermission("fiesta.tag.builder"))return true;
  UUID target=p.getUniqueId();
  for(var e:grants.entrySet())if(e.getValue().contains(target)&&isBuilder(loadedGrantors.get(e.getKey())))return true;
  return false;
 }
 private boolean blocked(Player p,Location l){return inside(l)&&!allowed(p);}
 private Player actor(Entity e){if(e instanceof Player)return (Player)e;if(e instanceof Projectile){ProjectileSource s=((Projectile)e).getShooter();if(s instanceof Player)return (Player)s;}return null;}
 private OfflinePlayer known(String name){
  if(!name.matches("[A-Za-z0-9_.]{1,17}"))return null;
  Player online=Bukkit.getPlayerExact(name);if(online!=null)return online;
  return Bukkit.getOfflinePlayerIfCached(name);
 }
 private void msg(CommandSender s,String m){s.sendMessage("§8[§aSpawn§8] §r"+m);}
 @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
  if(sender instanceof Player&&!signedIn((Player)sender)){msg(sender,"§cPlease authenticate first.");return true;}
  if(command.getName().equalsIgnoreCase("spawntag"))return tag(sender,args);
  if(command.getName().equalsIgnoreCase("spawntrust"))return trust(sender,args);
  if(command.getName().equalsIgnoreCase("trust"))return trustShortcut(sender,args);
  return false;
 }
 private boolean trustShortcut(CommandSender s,String[] a){
  if(a.length==0||(a.length>=1&&a[0].equalsIgnoreCase("gui"))){
   if(!(s instanceof Player)){msg(s,"§cThe trust menu is available in game.");return true;}
   if(a.length>2){msg(s,"§e/trust gui [builder]");return true;}
   Player p=(Player)s;UUID owner=p.getUniqueId();
   if(a.length==2){if(!p.hasPermission("fiesta.spawntrust.admin")){msg(p,"§cOnly staff can manage another builder's list.");return true;}OfflinePlayer b=known(a[1]);if(b==null){msg(p,"§cBuilder not found.");return true;}owner=b.getUniqueId();}
   if(!canManage(p,owner)){msg(p,"§cOnly builders can manage their trust list.");return true;}
   showTrustMenu(p,owner,0,false,null);return true;
  }
  if(a.length==1&&a[0].equalsIgnoreCase("list"))return trust(s,new String[]{"list"});
  if(a.length==2&&a[0].equalsIgnoreCase("list"))return trust(s,new String[]{"list",a[1]});
  boolean remove=a.length>0&&a[0].equalsIgnoreCase("remove");int offset=remove?1:0;
  if(a.length<offset+1||a.length>offset+3){msg(s,"§e/trust <player> <builder|player> [builder-owner] | /trust remove <player> <builder|player> [builder-owner]");return true;}
  String target=a[offset];String type=a.length>offset+1?a[offset+1].toLowerCase(Locale.ROOT):"player";
  if(type.equals("builder")){
   if(a.length!=offset+2){msg(s,"§e/trust <player> builder or /trust remove <player> builder");return true;}
   if(!s.hasPermission("fiesta.spawntag.manage")){msg(s,"§cOnly staff can grant or remove the builder tag.");return true;}
   return tag(s,new String[]{remove?"revoke":"grant",target,"builder"});
  }
  if(!type.equals("player")){msg(s,"§cChoose player or builder.");return true;}
  String action=remove?"remove":"add";
  if(a.length==offset+3)return trust(s,new String[]{action,target,a[offset+2]});
  if(s instanceof Player&&((Player)s).hasPermission("fiesta.tag.builder"))return trust(s,new String[]{action,target});
  if(s.hasPermission("fiesta.spawntrust.admin")){msg(s,"§eSpecify the builder: /trust "+(remove?"remove ":"")+target+" player <builder-owner>");return true;}
  return trust(s,new String[]{action,target});
 }
 @Override public java.util.List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
  if(!command.getName().equalsIgnoreCase("trust"))return java.util.Collections.emptyList();
  java.util.List<String> result=new java.util.ArrayList<>();
  boolean remove=args.length>0&&args[0].equalsIgnoreCase("remove");int offset=remove?1:0;
  if(args.length==1){result.add("gui");result.add("list");result.add("remove");for(Player p:Bukkit.getOnlinePlayers())result.add(p.getName());}
  else if(args.length==2&&args[0].equalsIgnoreCase("gui")&&sender.hasPermission("fiesta.spawntrust.admin")){for(Player p:Bukkit.getOnlinePlayers())result.add(p.getName());}
  else if(args.length==offset+1){for(Player p:Bukkit.getOnlinePlayers())result.add(p.getName());}
  else if(args.length==offset+2){result.add("player");if(sender.hasPermission("fiesta.spawntag.manage"))result.add("builder");}
  else if(args.length==offset+3&&sender.hasPermission("fiesta.spawntrust.admin")){for(Player p:Bukkit.getOnlinePlayers())result.add(p.getName());}
  String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);
  return result.stream().filter(x->x.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().collect(Collectors.toList());
 }
 private boolean tag(CommandSender s,String[] a){
  if(!s.hasPermission("fiesta.spawntag.manage")){msg(s,"§cOnly staff can manage tags.");return true;}
  if(a.length<2){msg(s,"§e/spawntag <grant|revoke|list> <player> [tag]");return true;}
  String action=a[0].toLowerCase(Locale.ROOT);OfflinePlayer target=known(a[1]);
  if(target==null){msg(s,"§cPlayer not found. They must have joined before.");return true;}
  UUID id=target.getUniqueId();
  if(action.equals("list")){
   luck.getUserManager().loadUser(id).thenAccept(u->{String list=u.getNodes().stream().filter(n->n.getKey().startsWith("fiesta.tag.")&&n.getValue()).map(n->n.getKey().substring("fiesta.tag.".length())).sorted().collect(Collectors.joining(", "));Bukkit.getScheduler().runTask(this,()->msg(s,"§7Tags for "+target.getName()+": §f"+(list.isEmpty()?"none":list)));});return true;
  }
  if(a.length!=3||(!action.equals("grant")&&!action.equals("revoke"))||!a[2].matches("[a-zA-Z0-9_-]{1,32}")){msg(s,"§e/spawntag <grant|revoke|list> <player> [tag]");return true;}
  String tag=a[2].toLowerCase(Locale.ROOT);boolean giving=action.equals("grant");Node node=Node.builder("fiesta.tag."+tag).build();
  luck.getUserManager().modifyUser(id,u->{if(giving)u.data().add(node);else u.data().remove(node);loadedGrantors.put(id,u);}).whenComplete((v,err)->Bukkit.getScheduler().runTask(this,()->{
   if(err!=null){msg(s,"§cTag update failed; no success was reported.");getLogger().warning("Tag update failed: "+err);return;}
   if(!giving&&tag.equals("builder")){Set<UUID> old=grants.remove(id);if(old!=null)try{saveTrust();}catch(IOException e){grants.put(id,old);getLogger().severe("Trust save failed after builder revocation: "+e);msg(s,"§cCould not save trust revocation; contact an admin.");return;}}
   getLogger().info("Tag "+action+" "+tag+" for "+id+" by "+s.getName());msg(s,"§aTag "+tag+" "+(giving?"granted to ":"removed from ")+target.getName()+".");
  }));return true;
 }
 private boolean trust(CommandSender s,String[] a){return trust(s,a,null);}
 private boolean trust(CommandSender s,String[] a,Runnable onSuccess){
  if(a.length<1||(!a[0].equalsIgnoreCase("list")&&a.length<2)){msg(s,"§e/spawntrust <add|remove|list> [player] [builder]");return true;}
  boolean admin=s.hasPermission("fiesta.spawntrust.admin");UUID owner;
  if(a.length>=3||(a[0].equalsIgnoreCase("list")&&a.length>=2)){
   if(!admin){msg(s,"§cOnly staff can manage another builder's list.");return true;}
   String ownerName=a[0].equalsIgnoreCase("list")?a[1]:a[2];OfflinePlayer op=known(ownerName);if(op==null){msg(s,"§cBuilder not found.");return true;}owner=op.getUniqueId();
  }else{
   if(!(s instanceof Player)){msg(s,"§cSpecify a builder.");return true;}Player p=(Player)s;
   if(!p.hasPermission("fiesta.tag.builder")){msg(s,"§cOnly builders can manage their trust list.");return true;}owner=p.getUniqueId();
  }
  if(a[0].equalsIgnoreCase("list")){
   Set<UUID> set=grants.getOrDefault(owner,Collections.emptySet());String names=set.stream().map(id->{OfflinePlayer p=Bukkit.getOfflinePlayer(id);return p.getName()!=null?p.getName():id.toString();}).sorted().collect(Collectors.joining(", "));msg(s,"§7Trusted: §f"+(names.isEmpty()?"none":names));return true;
  }
  if(!a[0].equalsIgnoreCase("add")&&!a[0].equalsIgnoreCase("remove")){msg(s,"§eUse add, remove, or list.");return true;}
  OfflinePlayer target=known(a[1]);if(target==null){msg(s,"§cPlayer not found. They must have joined before.");return true;}
  if(target.getUniqueId().equals(owner)){msg(s,"§cA builder doesn't need to trust themselves.");return true;}
  UUID id=target.getUniqueId();boolean add=a[0].equalsIgnoreCase("add");
  luck.getUserManager().loadUser(owner).thenAccept(u->Bukkit.getScheduler().runTask(this,()->{
   if(s instanceof Player&&(!((Player)s).isOnline()||!canManage((Player)s,owner))){msg(s,"§cYou no longer have permission to manage this list.");return;}
   loadedGrantors.put(owner,u);
   if(add&&!isBuilder(u)){msg(s,"§cThat player does not have the builder tag.");return;}
   Set<UUID> set=grants.computeIfAbsent(owner,k->new HashSet<>());boolean changed=add?set.add(id):set.remove(id);
   if(!changed){msg(s,add?"§eAlready trusted.":"§eThat player is not on this list.");return;}
   try{saveTrust();}catch(IOException ex){if(add)set.remove(id);else set.add(id);msg(s,"§cCould not save trust; no change was applied.");getLogger().severe("Trust save failed: "+ex);return;}
   getLogger().info("Trust "+(add?"added ":"removed ")+id+" by "+s.getName()+" for builder "+owner);msg(s,"§a"+target.getName()+(add?" can now help build at spawn.":" was removed from this trust list."));
   if(onSuccess!=null)onSuccess.run();
  })).exceptionally(ex->{Bukkit.getScheduler().runTask(this,()->msg(s,"§cCould not check builder permissions."));return null;});return true;
 }
 private boolean canManage(Player p,UUID owner){return signedIn(p)&&(p.hasPermission("fiesta.spawntrust.admin")||(p.getUniqueId().equals(owner)&&p.hasPermission("fiesta.tag.builder")));}
 private static final class TrustMenu implements InventoryHolder {
  final UUID owner; final int page; final boolean adding; final UUID confirm; Inventory inventory;
  final Map<Integer,UUID> entries=new HashMap<>();
  TrustMenu(UUID owner,int page,boolean adding,UUID confirm){this.owner=owner;this.page=page;this.adding=adding;this.confirm=confirm;}
  public Inventory getInventory(){return inventory;}
 }
 private ItemStack button(Material material,String title,String... lore){ItemStack i=new ItemStack(material);ItemMeta m=i.getItemMeta();m.setDisplayName(title);m.setLore(Arrays.asList(lore));i.setItemMeta(m);return i;}
 private void showTrustMenu(Player p,UUID owner,int page,boolean adding,UUID confirm){
  if(!p.isOnline()||!canManage(p,owner))return;
  String ownerName=Optional.ofNullable(Bukkit.getOfflinePlayer(owner).getName()).orElse(owner.toString());
  TrustMenu h=new TrustMenu(owner,page,adding,confirm);h.inventory=Bukkit.createInventory(h,54,"§2Spawn trust: "+ownerName);
  if(confirm!=null){
   String name=Optional.ofNullable(Bukkit.getOfflinePlayer(confirm).getName()).orElse(confirm.toString());
   h.inventory.setItem(22,button(Material.RED_CONCRETE,"§cRemove "+name+"?","§7Click to confirm removal."));
   h.inventory.setItem(49,button(Material.ARROW,"§eCancel"));p.openInventory(h.inventory);return;
  }
  Set<UUID> current=grants.getOrDefault(owner,Collections.emptySet());
  List<UUID> entries=(adding?Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).filter(id->!id.equals(owner)&&!current.contains(id)):current.stream()).sorted(java.util.Comparator.comparing(id->Optional.ofNullable(Bukkit.getOfflinePlayer(id).getName()).orElse(id.toString()),String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());
  int maxPage=Math.max(0,(entries.size()-1)/45);page=Math.min(Math.max(0,page),maxPage);
  if(page!=h.page){showTrustMenu(p,owner,page,adding,null);return;}
  for(int i=page*45;i<Math.min(entries.size(),page*45+45);i++){
   UUID id=entries.get(i);OfflinePlayer target=Bukkit.getOfflinePlayer(id);ItemStack head=new ItemStack(Material.PLAYER_HEAD);ItemMeta meta=head.getItemMeta();meta.setDisplayName("§f"+Optional.ofNullable(target.getName()).orElse(id.toString()));meta.setLore(List.of(adding?"§aClick to add as a helper":"§cClick to remove from this list"));head.setItemMeta(meta);int slot=i-page*45;h.entries.put(slot,id);h.inventory.setItem(slot,head);
  }
  if(page>0)h.inventory.setItem(45,button(Material.ARROW,"§ePrevious page"));
  if(page<maxPage)h.inventory.setItem(53,button(Material.ARROW,"§eNext page"));
  h.inventory.setItem(49,adding?button(Material.ARROW,"§eBack to trusted players","§7Offline player? Use /trust <name> player."):button(Material.LIME_DYE,"§aAdd an online player","§7Offline player? Use /trust <name> player."));
  h.inventory.setItem(47,button(Material.BOOK,"§eSpawn trust","§7Helpers can build at spawn,","§7but cannot add others."));p.openInventory(h.inventory);
 }
 private void nextMenu(Player p,TrustMenu current,int page,boolean adding,UUID confirm){Bukkit.getScheduler().runTask(this,()->{if(p.isOnline()&&p.getOpenInventory().getTopInventory().getHolder()==current)showTrustMenu(p,current.owner,page,adding,confirm);});}
 @EventHandler(priority=EventPriority.HIGHEST) public void menuClick(InventoryClickEvent e){
  if(!(e.getView().getTopInventory().getHolder() instanceof TrustMenu))return;
  e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player))return;Player p=(Player)e.getWhoClicked();TrustMenu h=(TrustMenu)e.getView().getTopInventory().getHolder();
  if(!canManage(p,h.owner)){Bukkit.getScheduler().runTask(this,()->{if(p.isOnline()&&p.getOpenInventory().getTopInventory().getHolder()==h)p.closeInventory();});msg(p,"§cYou no longer have permission to manage this list.");return;}
  int slot=e.getRawSlot();if(slot<0||slot>=54)return;
  if(h.confirm!=null){if(slot==49){nextMenu(p,h,h.page,false,null);return;}if(slot!=22)return;menuChange(p,h.owner,h.confirm,false,h.page);return;}
  if(slot==45&&h.page>0){nextMenu(p,h,h.page-1,h.adding,null);return;}
  if(slot==53){nextMenu(p,h,h.page+1,h.adding,null);return;}
  if(slot==49){nextMenu(p,h,0,!h.adding,null);return;}
  UUID target=h.entries.get(slot);if(target==null)return;
  if(h.adding)menuChange(p,h.owner,target,true,h.page);else nextMenu(p,h,h.page,false,target);
 }
 private void menuChange(Player p,UUID owner,UUID target,boolean add,int page){
  if(!canManage(p,owner))return;
  String name=Bukkit.getOfflinePlayer(target).getName(),ownerName=Bukkit.getOfflinePlayer(owner).getName();
  if(name==null||ownerName==null){msg(p,"§cPlayer name unavailable; use the command instead.");return;}
  String[] args=p.hasPermission("fiesta.spawntrust.admin")?new String[]{add?"add":"remove",name,ownerName}:new String[]{add?"add":"remove",name};
  Inventory opened=p.getOpenInventory().getTopInventory();
  trust(p,args,()->{if(p.isOnline()&&p.getOpenInventory().getTopInventory().equals(opened))showTrustMenu(p,owner,page,false,null);});
 }
 @EventHandler(priority=EventPriority.HIGHEST) public void menuDrag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof TrustMenu)e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void place(BlockPlaceEvent e){if(blocked(e.getPlayer(),e.getBlock().getLocation()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void multi(BlockMultiPlaceEvent e){if(e.getReplacedBlockStates().stream().anyMatch(s->blocked(e.getPlayer(),s.getLocation())))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void breaks(BlockBreakEvent e){if(blocked(e.getPlayer(),e.getBlock().getLocation()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void bucketEmpty(PlayerBucketEmptyEvent e){if(blocked(e.getPlayer(),e.getBlockClicked().getLocation())||blocked(e.getPlayer(),e.getBlock().getLocation()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void bucketFill(PlayerBucketFillEvent e){if(blocked(e.getPlayer(),e.getBlockClicked().getLocation())||blocked(e.getPlayer(),e.getBlock().getLocation()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void interact(PlayerInteractEvent e){
  Block b=e.getClickedBlock();if(b==null||!blocked(e.getPlayer(),b.getLocation()))return;
  Material t=b.getType();if(e.getAction()==org.bukkit.event.block.Action.PHYSICAL){if(t==Material.FARMLAND||t==Material.TURTLE_EGG||t==Material.SNIFFER_EGG)e.setCancelled(true);return;}
  if(e.getAction()==org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK){String n=t.name();
   boolean safeDoor=(n.endsWith("_DOOR")||n.endsWith("_GATE"))&&!n.startsWith("IRON_");
   if(!safeDoor)e.setUseInteractedBlock(Event.Result.DENY);
   ItemStack item=e.getItem();if(item!=null&&!item.getType().isAir())e.setUseItemInHand(Event.Result.DENY);
  }
 }
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityInteract(PlayerInteractEntityEvent e){if(blocked(e.getPlayer(),e.getRightClicked().getLocation())&&!(e.getRightClicked() instanceof Player))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityDamage(EntityDamageByEntityEvent e){if(e.getEntity() instanceof Player)return;Player p=actor(e.getDamager());if(p!=null&&blocked(p,e.getEntity().getLocation()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void environmentalHanging(org.bukkit.event.hanging.HangingBreakEvent e){if(inside(e.getEntity().getLocation())&&e.getCause()!=org.bukkit.event.hanging.HangingBreakEvent.RemoveCause.ENTITY)e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explosiveDamage(EntityDamageEvent e){if(!(e.getEntity() instanceof Player)&&inside(e.getEntity().getLocation())&&(e.getCause()==EntityDamageEvent.DamageCause.ENTITY_EXPLOSION||e.getCause()==EntityDamageEvent.DamageCause.BLOCK_EXPLOSION))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void inventoryClick(InventoryClickEvent e){Location l=e.getView().getTopInventory().getLocation();if(e.getWhoClicked() instanceof Player&&blocked((Player)e.getWhoClicked(),l))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void inventoryDrag(InventoryDragEvent e){Location l=e.getView().getTopInventory().getLocation();if(e.getWhoClicked() instanceof Player&&blocked((Player)e.getWhoClicked(),l))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityBreak(org.bukkit.event.hanging.HangingBreakByEntityEvent e){Player p=actor(e.getRemover());if(p!=null&&blocked(p,e.getEntity().getLocation()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityPlace(org.bukkit.event.hanging.HangingPlaceEvent e){if(e.getPlayer()!=null&&blocked(e.getPlayer(),e.getBlock().getLocation()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void container(InventoryOpenEvent e){if(!(e.getPlayer() instanceof Player))return;Location l=e.getInventory().getLocation();if(blocked((Player)e.getPlayer(),l))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explosion(EntityExplodeEvent e){e.blockList().removeIf(b->inside(b.getLocation()));}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explosion(BlockExplodeEvent e){e.blockList().removeIf(b->inside(b.getLocation()));}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityChange(EntityChangeBlockEvent e){if(inside(e.getBlock().getLocation()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void burn(BlockBurnEvent e){if(inside(e.getBlock().getLocation()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void ignite(BlockIgniteEvent e){if(inside(e.getBlock().getLocation())&&(e.getPlayer()==null||!allowed(e.getPlayer())))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void flow(BlockFromToEvent e){if(inside(e.getToBlock().getLocation())&&!inside(e.getBlock().getLocation()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void extend(BlockPistonExtendEvent e){boolean piston=inside(e.getBlock().getLocation());for(Block b:e.getBlocks()){boolean from=inside(b.getLocation()),to=inside(b.getRelative(e.getDirection()).getLocation());if(from!=to||(!piston&&(from||to))){e.setCancelled(true);return;}}}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void retract(BlockPistonRetractEvent e){boolean piston=inside(e.getBlock().getLocation());for(Block b:e.getBlocks()){boolean from=inside(b.getLocation()),to=inside(b.getRelative(e.getDirection()).getLocation());if(from!=to||(!piston&&(from||to))){e.setCancelled(true);return;}}}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hopper(InventoryMoveItemEvent e){Location a=e.getSource().getLocation(),b=e.getDestination().getLocation();if(a!=null&&b!=null&&inside(a)!=inside(b))e.setCancelled(true);}
}
