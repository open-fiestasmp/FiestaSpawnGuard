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
import net.luckperms.api.util.Tristate;
import net.luckperms.api.query.QueryOptions;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import fr.xephi.authme.api.v3.AuthMeApi;

public final class SpawnGuard extends JavaPlugin implements Listener {
 private LuckPerms luck; private String world; private int cx,cz,radius; private File trustFile;
 private final Map<UUID,Set<UUID>> grants=new HashMap<>();
 private final Map<UUID,User> loadedGrantors=new ConcurrentHashMap<>();
 private final Set<UUID> grantorIds=ConcurrentHashMap.newKeySet();
 private boolean ready=false;
 private boolean authApiWarningLogged=false;
 private boolean trustStoreHealthy=true;
 @Override public void onEnable(){
  try{
   saveDefaultConfig(); world=getConfig().getString("world","world");
   cx=Math.floorDiv(getConfig().getInt("center-x",0),16);cz=Math.floorDiv(getConfig().getInt("center-z",0),16);
   radius=getConfig().getInt("radius-chunks",32);
   if(radius<16||radius>64||Bukkit.getWorld(world)==null)throw new IllegalStateException("Invalid spawn protection configuration");
   luck=LuckPermsProvider.get();trustFile=new File(getDataFolder(),"trust.yml");
   try{loadTrust();}catch(IOException ex){getLogger().severe("Could not load trust.yml; helper access and trust-list commands are disabled until staff restore the file and restart. The file was left untouched.");}
   grantorIds.addAll(grants.keySet());
   for(UUID builder:grantorIds)luck.getUserManager().loadUser(builder).whenComplete((user,error)->{
    if(error!=null){getLogger().warning("Could not load builder permissions for "+builder+": "+error);return;}
    if(user!=null)loadedGrantors.put(builder,user);
   });
   luck.getEventBus().subscribe(this,UserDataRecalculateEvent.class,e->{UUID id=e.getUser().getUniqueId();if(grantorIds.contains(id))loadedGrantors.put(id,e.getUser());});
   Bukkit.getPluginManager().registerEvents(this,this);ready=true;
   getLogger().info("Enabled spawn guard for "+world+" centered at chunk "+cx+","+cz+" radius "+radius+"; trusts="+grants.values().stream().mapToInt(Set::size).sum());
  }catch(Exception e){getLogger().severe("Spawn guard startup failed: "+e);Bukkit.getPluginManager().disablePlugin(this);}
 }
 private void loadTrust()throws IOException{
  grants.clear();
  if(!trustFile.exists()){trustStoreHealthy=true;return;}
  Map<UUID,Set<UUID>> loaded=new HashMap<>();YamlConfiguration cfg=new YamlConfiguration();
  try{
   cfg.load(trustFile);
   for(String key:cfg.getKeys(false)){
    UUID owner=UUID.fromString(key);Object raw=cfg.get(key);
    if(!(raw instanceof List<?> values))throw new IOException("Invalid helper list");
    Set<UUID> targets=new HashSet<>();
    for(Object value:values){if(!(value instanceof String target))throw new IOException("Invalid helper UUID");targets.add(UUID.fromString(target));}
    loaded.put(owner,targets);
   }
  }catch(Exception ex){trustStoreHealthy=false;throw new IOException("Cannot load trust file",ex);}
  grants.putAll(loaded);trustStoreHealthy=true;
 }
 private void saveTrust()throws IOException{
  YamlConfiguration cfg=new YamlConfiguration();for(var e:grants.entrySet())cfg.set(e.getKey().toString(),e.getValue().stream().map(UUID::toString).sorted().toList());
  File tmp=new File(getDataFolder(),"trust.yml.tmp");cfg.save(tmp);
  try{Files.move(tmp.toPath(),trustFile.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp.toPath(),trustFile.toPath(),StandardCopyOption.REPLACE_EXISTING);}
 }
 private boolean inside(Location l){if(l==null||l.getWorld()==null||!world.equals(l.getWorld().getName()))return false;long dx=(l.getBlockX()>>4)-cx,dz=(l.getBlockZ()>>4)-cz;return dx*dx+dz*dz<=(long)radius*radius;}
 private boolean signedIn(Player p){try{return AuthMeApi.getInstance().isAuthenticated(p);}catch(RuntimeException e){if(!authApiWarningLogged){authApiWarningLogged=true;getLogger().warning("AuthMe authentication check failed; spawn access is denied until it recovers: "+e);}return false;}}
 private boolean isBuilder(User u){
  if(u==null)return false;
  Player online=Bukkit.getPlayer(u.getUniqueId());if(online!=null)return online.hasPermission("fiesta.tag.builder");
  Tristate value=u.getCachedData().getPermissionData().checkPermission("fiesta.tag.builder");
  if(value==Tristate.TRUE)return true;
  if(!Bukkit.getOfflinePlayer(u.getUniqueId()).isOp())return false;
  return u.resolveInheritedNodes(QueryOptions.nonContextual()).stream().noneMatch(n->!n.getValue()&&(n.getKey().equals("fiesta.tag.builder")||n.getKey().equals("fiesta.tag.*")||n.getKey().equals("fiesta.*")||n.getKey().equals("*")));
 }
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
  if(a.length==0){
   if(!(s instanceof Player)){msg(s,"§cThe trust menu is available in game.");return true;}
   Player p=(Player)s;
   if(p.hasPermission("fiesta.spawntag.manage")){showStaffTargets(p,0);return true;}
   UUID owner=p.getUniqueId();
   if(!canManage(p,owner)){msg(p,"§cOnly builders can manage their trust list.");return true;}
   showTrustMenu(p,owner,0,false,null);return true;
  }
  if(a[0].equalsIgnoreCase("gui")){
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
 private boolean tag(CommandSender s,String[] a){return tag(s,a,null);}
 private boolean tag(CommandSender s,String[] a,Runnable onSuccess){
  if(!s.hasPermission("fiesta.spawntag.manage")){msg(s,"§cOnly staff can manage tags.");return true;}
  if(a.length<2){msg(s,"§e/spawntag <grant|revoke|list> <player> [tag]");return true;}
  String action=a[0].toLowerCase(Locale.ROOT);OfflinePlayer target=known(a[1]);
  if(target==null){msg(s,"§cPlayer not found. They must have joined before.");return true;}
  UUID id=target.getUniqueId();
  if(action.equals("list")){
   luck.getUserManager().loadUser(id).thenAccept(u->Bukkit.getScheduler().runTask(this,()->{String list=u.getNodes().stream().filter(n->n.getKey().startsWith("fiesta.tag.")&&n.getValue()).map(n->n.getKey().substring("fiesta.tag.".length())).sorted().collect(Collectors.joining(", "));msg(s,"§7Assigned tags for "+target.getName()+": §f"+(list.isEmpty()?"none":list)+" §7| Builder access: §f"+(isBuilder(u)?"enabled":"disabled"));}));return true;
  }
  if(a.length!=3||(!action.equals("grant")&&!action.equals("revoke"))||!a[2].matches("[a-zA-Z0-9_-]{1,32}")){msg(s,"§e/spawntag <grant|revoke|list> <player> [tag]");return true;}
  String tag=a[2].toLowerCase(Locale.ROOT);boolean giving=action.equals("grant");Node node=Node.builder("fiesta.tag."+tag).build();Node denied=Node.builder("fiesta.tag."+tag).value(false).build();
  luck.getUserManager().modifyUser(id,u->{if(tag.equals("builder")){u.data().remove(node);u.data().remove(denied);u.data().add(giving?node:denied);}else if(giving)u.data().add(node);else u.data().remove(node);loadedGrantors.put(id,u);}).whenComplete((v,err)->Bukkit.getScheduler().runTask(this,()->{
   if(err!=null){msg(s,"§cTag update failed; no success was reported.");getLogger().warning("Tag update failed: "+err);return;}
   if(!giving&&tag.equals("builder")){Set<UUID> old=grants.remove(id);if(old!=null)try{saveTrust();}catch(IOException e){grants.put(id,old);getLogger().severe("Trust save failed after builder revocation: "+e);msg(s,"§cCould not save trust revocation; contact an admin.");return;}grantorIds.remove(id);loadedGrantors.remove(id);}
   getLogger().info("Tag "+action+" "+tag+" for "+id+" by "+s.getName());msg(s,"§aTag "+tag+" "+(giving?"granted to ":"removed from ")+target.getName()+".");if(onSuccess!=null)onSuccess.run();
  }));return true;
 }
 private boolean trust(CommandSender s,String[] a){return trust(s,a,null);}
 private boolean trust(CommandSender s,String[] a,Runnable onSuccess){
  if(!trustStoreHealthy){msg(s,"§cThe trust file is unreadable. Helpers are denied until staff restore it and restart the server.");return true;}
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
  UUID id=target.getUniqueId();boolean add=a[0].equalsIgnoreCase("add");
  luck.getUserManager().loadUser(owner).thenAccept(u->Bukkit.getScheduler().runTask(this,()->{
   if(s instanceof Player&&(!((Player)s).isOnline()||!canManage((Player)s,owner))){msg(s,"§cYou no longer have permission to manage this list.");return;}
   if(add&&!isBuilder(u)){msg(s,"§cThat player no longer has builder access.");return;}
   loadedGrantors.put(owner,u);
   Set<UUID> set=grants.computeIfAbsent(owner,k->new HashSet<>());boolean changed=add?set.add(id):set.remove(id);
   if(!changed){msg(s,add?"§eAlready trusted.":"§eThat player is not on this list.");return;}
   try{saveTrust();}catch(IOException ex){if(add)set.remove(id);else set.add(id);msg(s,"§cCould not save trust; no change was applied.");getLogger().severe("Trust save failed: "+ex);return;}
   grantorIds.add(owner);
   getLogger().info("Trust "+(add?"added ":"removed ")+id+" by "+s.getName()+" for builder "+owner);msg(s,"§a"+target.getName()+(add?" can now help build at spawn.":" was removed from this trust list."));
   if(onSuccess!=null)onSuccess.run();
  })).exceptionally(ex->{getLogger().warning("Could not load builder permissions for a trust change: "+ex);Bukkit.getScheduler().runTask(this,()->msg(s,"§cCould not check builder permissions."));return null;});return true;
 }
 private boolean canManage(Player p,UUID owner){return signedIn(p)&&(p.hasPermission("fiesta.spawntrust.admin")||(p.getUniqueId().equals(owner)&&p.hasPermission("fiesta.tag.builder")));}
 private static final class TrustMenu implements InventoryHolder {
  final UUID owner; final int page; final boolean adding; final UUID confirm; Inventory inventory;
  final Map<Integer,UUID> entries=new HashMap<>();
  TrustMenu(UUID owner,int page,boolean adding,UUID confirm){this.owner=owner;this.page=page;this.adding=adding;this.confirm=confirm;}
  public Inventory getInventory(){return inventory;}
 }
 private enum StaffMenuMode{TARGETS,ACTIONS,OWNERS,CONFIRM_BUILDER}
 private static final class StaffMenu implements InventoryHolder {
  final StaffMenuMode mode; final UUID target; final int page,returnPage; final boolean builder,loading; Inventory inventory;
  final Map<Integer,UUID> entries=new HashMap<>();
  StaffMenu(StaffMenuMode mode,UUID target,int page,boolean builder,boolean loading){this(mode,target,page,builder,loading,page);}
  StaffMenu(StaffMenuMode mode,UUID target,int page,boolean builder,boolean loading,int returnPage){this.mode=mode;this.target=target;this.page=page;this.returnPage=returnPage;this.builder=builder;this.loading=loading;}
  public Inventory getInventory(){return inventory;}
 }
 private boolean staffMenuAllowed(Player p){return signedIn(p)&&p.hasPermission("fiesta.spawntag.manage");}
 private String playerName(UUID id){return Optional.ofNullable(Bukkit.getOfflinePlayer(id).getName()).orElse(null);}
 private void showStaffTargets(Player p,int page){
  if(!staffMenuAllowed(p))return;
  List<Player> targets=Bukkit.getOnlinePlayers().stream().sorted(java.util.Comparator.comparing(Player::getName,String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());
  int maxPage=Math.max(0,(targets.size()-1)/45);page=Math.min(Math.max(0,page),maxPage);
  StaffMenu h=new StaffMenu(StaffMenuMode.TARGETS,null,page,false,false);h.inventory=Bukkit.createInventory(h,54,"§2Spawn access: choose player");
  for(int i=page*45;i<Math.min(targets.size(),page*45+45);i++){
   Player target=targets.get(i);ItemStack head=new ItemStack(Material.PLAYER_HEAD);ItemMeta meta=head.getItemMeta();meta.setDisplayName("§f"+target.getName());meta.setLore(List.of("§7Choose builder or helper access."));head.setItemMeta(meta);int slot=i-page*45;h.entries.put(slot,target.getUniqueId());h.inventory.setItem(slot,head);
  }
  if(targets.isEmpty())h.inventory.setItem(22,button(Material.PAPER,"§eNo players are online"));
  if(page>0)h.inventory.setItem(45,button(Material.ARROW,"§ePrevious page"));
  if(page<maxPage)h.inventory.setItem(53,button(Material.ARROW,"§eNext page"));
  h.inventory.setItem(47,button(Material.BOOK,"§eOpen my helper list","§7Manage helpers for your own builder tag."));
  h.inventory.setItem(49,button(Material.BARRIER,"§cClose"));p.openInventory(h.inventory);
 }
 private void showStaffActions(Player p,UUID target,int page){
  if(!staffMenuAllowed(p))return;
  Player online=Bukkit.getPlayer(target);
  if(online!=null){renderStaffActions(p,target,page,online.hasPermission("fiesta.tag.builder"));return;}
  String name=Optional.ofNullable(playerName(target)).orElse(target.toString());
  StaffMenu h=new StaffMenu(StaffMenuMode.ACTIONS,target,page,false,true);h.inventory=Bukkit.createInventory(h,27,"§2Spawn role: "+name);
  h.inventory.setItem(13,button(Material.CLOCK,"§eChecking builder access..."));h.inventory.setItem(22,button(Material.ARROW,"§eBack"));h.inventory.setItem(26,button(Material.BARRIER,"§cClose"));p.openInventory(h.inventory);
  luck.getUserManager().loadUser(target).whenComplete((user,error)->Bukkit.getScheduler().runTask(this,()->{
   if(!p.isOnline()||p.getOpenInventory().getTopInventory().getHolder()!=h)return;
   if(error!=null||user==null){msg(p,"§cCould not check that player's access.");showStaffTargets(p,page);return;}
   renderStaffActions(p,target,page,isBuilder(user));
  }));
 }
 private void renderStaffActions(Player p,UUID target,int page,boolean builder){
  if(!staffMenuAllowed(p))return;
  String name=Optional.ofNullable(playerName(target)).orElse(target.toString());
  StaffMenu h=new StaffMenu(StaffMenuMode.ACTIONS,target,page,builder,false);h.inventory=Bukkit.createInventory(h,27,"§2Spawn role: "+name);
  h.inventory.setItem(11,button(builder?Material.RED_CONCRETE:Material.LIME_CONCRETE,builder?"§cRemove builder tag":"§aGrant builder tag",builder?"§7The player can no longer build at spawn.":"§7The player can build at protected spawn."));
  h.inventory.setItem(15,button(Material.PLAYER_HEAD,"§aAdd as a helper","§7Choose which builder's list to use.","§7Helpers cannot grant access."));
  h.inventory.setItem(22,button(Material.ARROW,"§eBack to players"));h.inventory.setItem(26,button(Material.BARRIER,"§cClose"));p.openInventory(h.inventory);
 }
 private void showStaffBuilderConfirm(Player p,UUID target,int page){
  if(!staffMenuAllowed(p))return;
  String name=Optional.ofNullable(playerName(target)).orElse(target.toString());
  StaffMenu h=new StaffMenu(StaffMenuMode.CONFIRM_BUILDER,target,page,true,false);h.inventory=Bukkit.createInventory(h,27,"§cRemove builder: "+name);
  h.inventory.setItem(11,button(Material.RED_CONCRETE,"§cConfirm removal","§7This removes spawn builder access."));
  h.inventory.setItem(15,button(Material.ARROW,"§eCancel"));h.inventory.setItem(26,button(Material.BARRIER,"§cClose"));p.openInventory(h.inventory);
 }
 private void showStaffOwners(Player p,UUID target,int page,int returnPage){
  if(!staffMenuAllowed(p))return;
  Set<UUID> candidates=new HashSet<>(grantorIds);
  for(OfflinePlayer operator:Bukkit.getOperators())candidates.add(operator.getUniqueId());
  for(Player online:Bukkit.getOnlinePlayers())if(online.hasPermission("fiesta.tag.builder"))candidates.add(online.getUniqueId());
  for(User user:luck.getUserManager().getLoadedUsers())if(isBuilder(user))candidates.add(user.getUniqueId());
  StaffMenu loading=new StaffMenu(StaffMenuMode.OWNERS,target,page,false,true,returnPage);loading.inventory=Bukkit.createInventory(loading,54,"§2Spawn helper: choose builder");
  loading.inventory.setItem(22,button(Material.CLOCK,"§eLoading builder list..."));loading.inventory.setItem(49,button(Material.ARROW,"§eBack"));p.openInventory(loading.inventory);
  CompletableFuture<?>[] pending=candidates.stream().filter(id->Bukkit.getPlayer(id)==null&&!loadedGrantors.containsKey(id)).map(id->luck.getUserManager().loadUser(id).thenAccept(user->{if(user!=null)loadedGrantors.put(id,user);})).toArray(CompletableFuture[]::new);
  CompletableFuture.allOf(pending).whenComplete((ignored,error)->Bukkit.getScheduler().runTask(this,()->{
   if(!p.isOnline()||p.getOpenInventory().getTopInventory().getHolder()!=loading)return;
   if(error!=null){msg(p,"§cCould not load builder access.");showStaffActions(p,target,returnPage);return;}
   List<UUID> owners=candidates.stream().filter(id->{Player online=Bukkit.getPlayer(id);return online!=null?online.hasPermission("fiesta.tag.builder"):isBuilder(loadedGrantors.get(id));}).sorted(java.util.Comparator.comparing(id->Optional.ofNullable(playerName(id)).orElse(id.toString()),String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());
   renderStaffOwners(p,target,page,returnPage,owners);
  }));
 }
 private void renderStaffOwners(Player p,UUID target,int page,int returnPage,List<UUID> owners){
  int maxPage=Math.max(0,(owners.size()-1)/45);page=Math.min(Math.max(0,page),maxPage);
  String targetName=Optional.ofNullable(playerName(target)).orElse(target.toString());
  StaffMenu h=new StaffMenu(StaffMenuMode.OWNERS,target,page,false,false,returnPage);h.inventory=Bukkit.createInventory(h,54,"§2Spawn helper: "+targetName);
  for(int i=page*45;i<Math.min(owners.size(),page*45+45);i++){
   UUID owner=owners.get(i);String name=Optional.ofNullable(playerName(owner)).orElse(owner.toString());ItemStack head=new ItemStack(Material.PLAYER_HEAD);ItemMeta meta=head.getItemMeta();meta.setDisplayName("§f"+name);meta.setLore(List.of("§7Add the selected player to this list."));head.setItemMeta(meta);int slot=i-page*45;h.entries.put(slot,owner);h.inventory.setItem(slot,head);
  }
  if(owners.isEmpty())h.inventory.setItem(22,button(Material.PAPER,"§eNo active builder lists found","§7A builder must be online or have a trust list."));
  if(page>0)h.inventory.setItem(45,button(Material.ARROW,"§ePrevious page"));
  if(page<maxPage)h.inventory.setItem(53,button(Material.ARROW,"§eNext page"));
  h.inventory.setItem(47,button(Material.BOOK,"§eHelpers cannot delegate","§7Only staff and the builder manage this list."));
  h.inventory.setItem(49,button(Material.ARROW,"§eBack"));p.openInventory(h.inventory);
 }
 private void staffMenuClick(Player p,StaffMenu h,int slot){
  if(h.loading||!staffMenuAllowed(p))return;
  if(h.mode==StaffMenuMode.TARGETS){
   if(slot==45&&h.page>0){showStaffTargets(p,h.page-1);return;}
   if(slot==53){showStaffTargets(p,h.page+1);return;}
   if(slot==47){showTrustMenu(p,p.getUniqueId(),0,false,null);return;}
   if(slot==49){p.closeInventory();return;}
   UUID target=h.entries.get(slot);if(target!=null)showStaffActions(p,target,h.page);return;
  }
  if(h.mode==StaffMenuMode.ACTIONS){
   if(slot==22){showStaffTargets(p,h.page);return;}
   if(slot==26){p.closeInventory();return;}
   if(slot==11){
    String name=playerName(h.target);if(name==null){msg(p,"§cPlayer name unavailable; use /trust <name> builder.");return;}
    if(h.builder){showStaffBuilderConfirm(p,h.target,h.page);return;}
    tag(p,new String[]{"grant",name,"builder"},()->showStaffActions(p,h.target,h.page));return;
   }
   if(slot==15){showStaffOwners(p,h.target,0,h.page);return;}
   return;
  }
  if(h.mode==StaffMenuMode.CONFIRM_BUILDER){
   if(slot==15){showStaffActions(p,h.target,h.page);return;}
   if(slot==26){p.closeInventory();return;}
   if(slot==11){String name=playerName(h.target);if(name==null){msg(p,"§cPlayer name unavailable; use /trust remove <name> builder.");return;}tag(p,new String[]{"revoke",name,"builder"},()->showStaffActions(p,h.target,h.page));}
   return;
  }
  if(slot==45&&h.page>0){showStaffOwners(p,h.target,h.page-1,h.returnPage);return;}
  if(slot==53){showStaffOwners(p,h.target,h.page+1,h.returnPage);return;}
  if(slot==49){showStaffActions(p,h.target,h.returnPage);return;}
  UUID owner=h.entries.get(slot);if(owner==null)return;
  String targetName=playerName(h.target),ownerName=playerName(owner);
  if(targetName==null||ownerName==null){msg(p,"§cPlayer name unavailable; use /trust <player> player <builder>.");return;}
  if(grants.getOrDefault(owner,Collections.emptySet()).contains(h.target)){msg(p,"§eThat player is already trusted. Use /trust gui "+ownerName+" to remove them.");return;}
  trust(p,new String[]{"add",targetName,ownerName},()->showStaffActions(p,h.target,h.returnPage));
 }
 private ItemStack button(Material material,String title,String... lore){ItemStack i=new ItemStack(material);ItemMeta m=i.getItemMeta();m.setDisplayName(title);m.setLore(Arrays.asList(lore));i.setItemMeta(m);return i;}
 private void showTrustMenu(Player p,UUID owner,int page,boolean adding,UUID confirm){
  if(!trustStoreHealthy){msg(p,"§cThe trust file is unreadable. Helpers are denied until staff restore it and restart the server.");return;}
  if(!p.isOnline()||!canManage(p,owner))return;
  String ownerName=Optional.ofNullable(Bukkit.getOfflinePlayer(owner).getName()).orElse(owner.toString());
  TrustMenu h=new TrustMenu(owner,page,adding,confirm);h.inventory=Bukkit.createInventory(h,54,"§2Spawn trust: "+ownerName);
  if(confirm!=null){
   String name=Optional.ofNullable(Bukkit.getOfflinePlayer(confirm).getName()).orElse(confirm.toString());
   h.inventory.setItem(22,button(Material.RED_CONCRETE,"§cRemove "+name+"?","§7Click to confirm removal."));
   h.inventory.setItem(49,button(Material.ARROW,"§eCancel"));p.openInventory(h.inventory);return;
  }
  Set<UUID> current=grants.getOrDefault(owner,Collections.emptySet());
  List<UUID> entries=(adding?Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).filter(id->!current.contains(id)):current.stream()).sorted(java.util.Comparator.comparing(id->Optional.ofNullable(Bukkit.getOfflinePlayer(id).getName()).orElse(id.toString()),String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());
  int maxPage=Math.max(0,(entries.size()-1)/45);page=Math.min(Math.max(0,page),maxPage);
  if(page!=h.page){showTrustMenu(p,owner,page,adding,null);return;}
  for(int i=page*45;i<Math.min(entries.size(),page*45+45);i++){
   UUID id=entries.get(i);OfflinePlayer target=Bukkit.getOfflinePlayer(id);ItemStack head=new ItemStack(Material.PLAYER_HEAD);ItemMeta meta=head.getItemMeta();meta.setDisplayName("§f"+Optional.ofNullable(target.getName()).orElse(id.toString()));meta.setLore(id.equals(owner)?List.of(adding?"§aClick to add yourself for testing":"§cClick to remove yourself", "§7This grants no extra access."):List.of(adding?"§aClick to add as a helper":"§cClick to remove from this list"));head.setItemMeta(meta);int slot=i-page*45;h.entries.put(slot,id);h.inventory.setItem(slot,head);
  }
  if(entries.isEmpty())h.inventory.setItem(22,button(Material.PAPER,adding?"§eNo online players to add":"§eNo helpers on this list","§7This menu lists helpers, not builders.","§7Use the green dye below to add someone."));
  if(page>0)h.inventory.setItem(45,button(Material.ARROW,"§ePrevious page"));
  if(page<maxPage)h.inventory.setItem(53,button(Material.ARROW,"§eNext page"));
  h.inventory.setItem(49,adding?button(Material.ARROW,"§eBack to trusted players","§7Offline player? Use /trust <name> player."):button(Material.LIME_DYE,"§aAdd an online player","§7Offline player? Use /trust <name> player."));
  h.inventory.setItem(47,button(Material.BOOK,"§eHelpers, not builder tags","§7Helpers can build at spawn,","§7but cannot add others.","§7To remove a builder tag, use","§f/trust remove <name> builder"));p.openInventory(h.inventory);
 }
 private void nextMenu(Player p,TrustMenu current,int page,boolean adding,UUID confirm){Bukkit.getScheduler().runTask(this,()->{if(p.isOnline()&&p.getOpenInventory().getTopInventory().getHolder()==current)showTrustMenu(p,current.owner,page,adding,confirm);});}
 @EventHandler(priority=EventPriority.HIGHEST) public void menuClick(InventoryClickEvent e){
  Object holder=e.getView().getTopInventory().getHolder();
  if(holder instanceof StaffMenu){e.setCancelled(true);if(e.getWhoClicked() instanceof Player)staffMenuClick((Player)e.getWhoClicked(),(StaffMenu)holder,e.getRawSlot());return;}
  if(!(holder instanceof TrustMenu))return;
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
  TrustMenu opened=(TrustMenu)p.getOpenInventory().getTopInventory().getHolder();
  trust(p,args,()->{if(p.isOnline()&&p.getOpenInventory().getTopInventory().getHolder()==opened)showTrustMenu(p,owner,page,false,null);});
 }
 @EventHandler(priority=EventPriority.HIGHEST) public void menuDrag(InventoryDragEvent e){Object holder=e.getView().getTopInventory().getHolder();if(holder instanceof TrustMenu||holder instanceof StaffMenu)e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void place(BlockPlaceEvent e){if(blocked(e.getPlayer(),e.getBlock().getLocation()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void multi(BlockMultiPlaceEvent e){if(e.getReplacedBlockStates().stream().anyMatch(s->blocked(e.getPlayer(),s.getLocation())))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void breaks(BlockBreakEvent e){if(blocked(e.getPlayer(),e.getBlock().getLocation()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void bucketEmpty(PlayerBucketEmptyEvent e){if(blocked(e.getPlayer(),e.getBlockClicked().getLocation())||blocked(e.getPlayer(),e.getBlock().getLocation()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void bucketFill(PlayerBucketFillEvent e){if(blocked(e.getPlayer(),e.getBlockClicked().getLocation())||blocked(e.getPlayer(),e.getBlock().getLocation()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void interact(PlayerInteractEvent e){
  Block b=e.getClickedBlock();if(b==null||!blocked(e.getPlayer(),b.getLocation()))return;
  Material t=b.getType();if(e.getAction()==org.bukkit.event.block.Action.PHYSICAL){if(t==Material.FARMLAND||t==Material.TURTLE_EGG||t==Material.SNIFFER_EGG)e.setCancelled(true);return;}
  if(e.getAction()==org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK){
   if(b.getState() instanceof InventoryHolder||b.getState() instanceof org.bukkit.block.Sign||t==Material.FLOWER_POT||t.name().startsWith("POTTED_"))e.setUseInteractedBlock(Event.Result.DENY);
   ItemStack item=e.getItem();if(item!=null&&!item.getType().isAir()){
    Material material=item.getType();String name=material.name();
    boolean modifiesBlock=name.endsWith("_HOE")||name.endsWith("_SHOVEL")||name.endsWith("_AXE")||name.endsWith("_SPAWN_EGG")||name.endsWith("_BOAT")||name.endsWith("_RAFT")||name.endsWith("_MINECART")||name.endsWith("_BUCKET")&&!name.equals("MILK_BUCKET")||material==Material.ARMOR_STAND||material==Material.SHEARS||material==Material.BRUSH||material==Material.FLINT_AND_STEEL||material==Material.FIRE_CHARGE||material==Material.BONE_MEAL||material==Material.END_CRYSTAL||material==Material.GLOW_INK_SAC||material==Material.INK_SAC||name.endsWith("_DYE")||material==Material.HONEYCOMB;
    if(modifiesBlock)e.setUseItemInHand(Event.Result.DENY);
   }
  }
 }
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityInteract(PlayerInteractEntityEvent e){Entity target=e.getRightClicked();if((target instanceof ArmorStand||target instanceof Hanging)&&blocked(e.getPlayer(),target.getLocation()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityDamage(EntityDamageByEntityEvent e){if(e.getEntity() instanceof Player||e.getEntity() instanceof Enemy)return;Player p=actor(e.getDamager());if(p!=null&&blocked(p,e.getEntity().getLocation()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void environmentalHanging(org.bukkit.event.hanging.HangingBreakEvent e){if(inside(e.getEntity().getLocation())&&e.getCause()!=org.bukkit.event.hanging.HangingBreakEvent.RemoveCause.ENTITY)e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explosiveDamage(EntityDamageEvent e){if(!(e.getEntity() instanceof Player)&&inside(e.getEntity().getLocation())&&(e.getCause()==EntityDamageEvent.DamageCause.ENTITY_EXPLOSION||e.getCause()==EntityDamageEvent.DamageCause.BLOCK_EXPLOSION))e.setCancelled(true);}
 private boolean worldContainer(Inventory inv){InventoryHolder h=inv.getHolder();return h instanceof BlockState||h instanceof DoubleChest||h instanceof Entity&&!(h instanceof Player)&&inv.getType()!=InventoryType.MERCHANT;}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void inventoryClick(InventoryClickEvent e){Inventory top=e.getView().getTopInventory();if(worldContainer(top)&&e.getWhoClicked() instanceof Player&&blocked((Player)e.getWhoClicked(),top.getLocation()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void inventoryDrag(InventoryDragEvent e){Inventory top=e.getView().getTopInventory();if(worldContainer(top)&&e.getWhoClicked() instanceof Player&&blocked((Player)e.getWhoClicked(),top.getLocation()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityBreak(org.bukkit.event.hanging.HangingBreakByEntityEvent e){Player p=actor(e.getRemover());if(p!=null&&blocked(p,e.getEntity().getLocation()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityPlace(org.bukkit.event.hanging.HangingPlaceEvent e){if(e.getPlayer()!=null&&blocked(e.getPlayer(),e.getBlock().getLocation()))e.setCancelled(true);}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void container(InventoryOpenEvent e){if(!(e.getPlayer() instanceof Player)||!worldContainer(e.getInventory()))return;Location l=e.getInventory().getLocation();if(blocked((Player)e.getPlayer(),l))e.setCancelled(true);}
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
