package am2.guis;

import am2.compass.*;
import am2.compass.client.AstralCompassPreferences;
import am2.compass.client.AstralCompassPreferences.Visit;
import am2.guis.controls.*;
import am2.items.ItemAstralCompass;
import am2.network.*;
import am2.playerextensions.ExtendedProperties;
import cpw.mods.fml.relauncher.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.util.*;

@SideOnly(Side.CLIENT)
public class GuiAstralCompass extends GuiScreen {
    private static final String[] CATEGORIES={"all","vanilla","am2","modded","favorites","history"};
    private final EntityPlayer player;
    private final int heldSlot;
    private GuiTextField searchField;
    private String category="all", selectedId=AstralStructureLocator.ID_STRONGHOLD, modFilter="";
    private Integer dimensionFilter;
    private List<StructureTargetInfo> filtered=new ArrayList<StructureTargetInfo>();
    private List<Visit> historyRows=new ArrayList<Visit>();
    private Visit selectedVisit;
    private int page, rows, left, top, panelWidth, panelHeight, listTop, footer, lastCatalogHash, ticks;
    private ItemAstralCompass.ClientSearchStatus lastStatus;
    private final AstralCompassPreferences prefs=AstralCompassPreferences.get();

    public GuiAstralCompass(EntityPlayer player) {
        this.player=player; heldSlot=player.inventory.currentItem;
        prefs.enterWorld();
        ItemAstralCompass.TargetData target=ItemAstralCompass.getTarget(player.getCurrentEquippedItem());
        if(target!=null) { selectedId=target.id; prefs.remember(target); }
    }
    public static String text(String key,Object... args) { return StatCollector.translateToLocalFormatted("am2.astral_compass.gui."+key,args); }
    private static String dim(int id) { return AstralCompassPreferences.dimensionName(id); }
    @Override public void initGui() {
        Keyboard.enableRepeatEvents(true);
        rows=Math.max(1,Math.min(6,(height-211)/32));
        panelWidth=Math.min(560,width-16); panelHeight=203+rows*32;
        left=(width-panelWidth)/2; top=Math.max(2,(height-panelHeight)/2);
        listTop=top+112; footer=listTop+rows*32+5;
        String query=searchField==null?"":searchField.getText();
        searchField=new GuiTextField(fontRendererObj,left+20,top+91,panelWidth-40,16);
        searchField.setMaxStringLength(64);searchField.setEnableBackgroundDrawing(false);searchField.setFocused(true);searchField.setText(query);
        requestCatalogSync(); rebuildFilter(false);
    }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }
    @Override public void updateScreen() {
        super.updateScreen(); searchField.updateCursorCounter(); ticks++;
        ItemAstralCompass.TargetData target=ItemAstralCompass.getTarget(player.getCurrentEquippedItem());
        prefs.remember(target);
        int hash=ItemAstralCompass.getClientCatalog().hashCode();
        ItemAstralCompass.ClientSearchStatus status=ItemAstralCompass.getClientSearchStatus();
        if(hash!=lastCatalogHash) rebuildFilter(false);
        else if(lastStatus!=status || ticks%20==0) refreshButtons();
        lastStatus=status;
    }
    private void rebuildFilter(boolean reset) {
        List<StructureTargetInfo> catalog=ItemAstralCompass.getClientCatalog(); lastCatalogHash=catalog.hashCode();
        String query=searchField==null?"":searchField.getText();
        String q=StructureTargetInfo.normalizeQuery(query), compact=StructureTargetInfo.compactQuery(q);
        filtered=new ArrayList<StructureTargetInfo>();historyRows=new ArrayList<Visit>();
        if("history".equals(category)) {
            for(Visit visit:prefs.history()) if(matches(visit.info(),q,compact)) {filtered.add(visit.info());historyRows.add(visit);}
        } else {
            for(StructureTargetInfo info:catalog) if(("all".equals(category)||"favorites".equals(category)||category.equals(info.category))
                    && (!"favorites".equals(category)||prefs.favorite(info.id)) && matches(info,q,compact)) filtered.add(info);
            Collections.sort(filtered,new Comparator<StructureTargetInfo>() {public int compare(StructureTargetInfo a,StructureTargetInfo b) {
                int favorite=Boolean.compare(prefs.favorite(b.id),prefs.favorite(a.id));
                if(favorite!=0)return favorite;
                int mod=a.getModName().compareToIgnoreCase(b.getModName());return mod!=0?mod:a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
            }});
        }
        boolean visible=false;for(StructureTargetInfo e:filtered)if(e.id.equals(selectedId)){visible=true;break;}
        if(!visible){selectedId=filtered.isEmpty()?"":filtered.get(0).id;selectedVisit=historyRows.isEmpty()?null:historyRows.get(0);}
        if(reset)page=0;page=Math.max(0,Math.min(page,Math.max(0,(filtered.size()-1)/rows)));refreshButtons();
    }
    private boolean matches(StructureTargetInfo info,String q,String compact) {
        return (modFilter.isEmpty()||modFilter.equals(info.getModId())) && (dimensionFilter==null||dimensionFilter==info.preferredDimension)
                && info.matchesQuery(q,compact);
    }
    private StructureTargetInfo selected() {
        if(selectedVisit!=null)return selectedVisit.info();
        for(StructureTargetInfo entry:ItemAstralCompass.getClientCatalog())if(entry.id.equals(selectedId))return entry;
        return null;
    }
    private GuiButton button(int id,int x,int y,int w,int h,String label,boolean enabled) {
        GuiButton button=new GuiButtonArcaneChalk(id,x,y,w,h,label);button.enabled=enabled;buttonList.add(button);return button;
    }
    private boolean busy() { return ItemAstralCompass.getClientSearchStatus().status==ItemAstralCompass.STATUS_SEARCHING; }
    @SuppressWarnings("unchecked") private void refreshButtons() {
        buttonList.clear();int tabW=(panelWidth-32-20)/6;
        for(int i=0;i<CATEGORIES.length;i++)buttonList.add(new GuiButtonArcaneChalk(20+i,left+16+i*(tabW+4),top+44,tabW,18,
                text("tab_"+CATEGORIES[i]),-1,CATEGORIES[i].equals(category)));
        int half=(panelWidth-36)/2;
        String mod="";for(StructureTargetInfo info:ItemAstralCompass.getClientCatalog())if(info.getModId().equals(modFilter)){mod=info.getModName();break;}
        button(8,left+16,top+66,half,18,text("mod_filter",modFilter.isEmpty()?text("all"):mod),true);
        button(9,left+20+half,top+66,half,18,text("dimension_filter",dimensionFilter==null?text("all"):dim(dimensionFilter)),true);
        for(int i=0;i<rows;i++) {int idx=page*rows+i;if(idx>=filtered.size())break;
            StructureTargetInfo entry=filtered.get(idx);buttonList.add(new StructureRow(100+idx,left+16,listTop+i*32,panelWidth-32,entry,
                    entry.id.equals(selectedId),prefs.favorite(entry.id),"history".equals(category)?historyRows.get(idx):null));
        }
        button(1,left+16,footer,24,18,"<",page>0);button(2,left+44,footer,24,18,">",(page+1)*rows<filtered.size());
        button(7,left+112,footer,Math.max(60,panelWidth-246),18,text(prefs.favorite(selectedId)?"unfavorite":"favorite"),selected()!=null);
        buttonList.add(new GuiButtonArcaneChalk(6,left+panelWidth-126,footer,24,18,"",GuiButtonArcaneChalk.GEAR,false));
        button(3,left+panelWidth-98,footer,82,18,text("done"),true);
        int actionW=(panelWidth-36)/2; StructureTargetInfo entry=selected();
        boolean rightDimension=entry!=null&&(!entry.dimensionRestricted||entry.preferredDimension==player.dimension);
        button(4,left+16,footer+23,actionW,22,busy()?text("cancel"):text("divinate",20),toolReady()&&(busy()||entry!=null&&rightDimension&&canAffordSearch()));
        button(5,left+20+actionW,footer+23,actionW,22,text("clear"),toolReady()&&!busy()&&ItemAstralCompass.hasTarget(player.getCurrentEquippedItem()));
    }
    private boolean canAffordSearch() {ExtendedProperties p=ExtendedProperties.For(player);return p!=null&&AstralStructureLocator.hasRequiredLevel(p.getMagicLevel())&&AstralStructureLocator.hasEnoughMana(p.getCurrentMana(),p.getBonusCurrentMana());}
    private boolean toolReady() {ItemStack stack=player.getCurrentEquippedItem();return player.inventory.currentItem==heldSlot&&stack!=null&&stack.getItem() instanceof ItemAstralCompass;}
    @Override protected void actionPerformed(GuiButton button) {
        if(button.id>=20&&button.id<26){category=CATEGORIES[button.id-20];selectedVisit=null;rebuildFilter(true);return;}
        if(button.id>=100){int idx=button.id-100;if(idx<filtered.size()){selectedId=filtered.get(idx).id;selectedVisit="history".equals(category)?historyRows.get(idx):null;refreshButtons();}return;}
        switch(button.id) {
            case 1:if(page>0)page--;refreshButtons();break;
            case 2:if((page+1)*rows<filtered.size())page++;refreshButtons();break;
            case 3:mc.displayGuiScreen(null);mc.setIngameFocus();break;
            case 4:if(busy())sendAction(ItemAstralCompass.ACT_CANCEL,"");else beginSearch();break;
            case 5:if(toolReady())sendAction(ItemAstralCompass.ACT_CLEAR,"");break;
            case 6:mc.displayGuiScreen(new GuiAstralCompassHudSettings(this));break;
            case 7:prefs.toggle(selectedId);rebuildFilter(false);break;
            case 8:cycleMod();break;
            case 9:cycleDimension();break;
        }
    }
    private void cycleMod() {
        TreeMap<String,String> mods=new TreeMap<String,String>();for(StructureTargetInfo e:ItemAstralCompass.getClientCatalog())mods.put(e.getModName(),e.getModId());
        List<String> ids=new ArrayList<String>();ids.add("");ids.addAll(mods.values());int at=ids.indexOf(modFilter);modFilter=ids.get((at+1)%ids.size());rebuildFilter(true);
    }
    private void cycleDimension() {
        TreeSet<Integer> ids=new TreeSet<Integer>();for(StructureTargetInfo e:ItemAstralCompass.getClientCatalog())ids.add(e.preferredDimension);
        List<Integer> dimensions=new ArrayList<Integer>();dimensions.add(null);dimensions.addAll(ids);int at=dimensions.indexOf(dimensionFilter);dimensionFilter=dimensions.get((at+1)%dimensions.size());rebuildFilter(true);
    }
    private void beginSearch() {
        StructureTargetInfo entry=selected();if(!toolReady()||busy()||!canAffordSearch()||entry==null||entry.dimensionRestricted&&entry.preferredDimension!=player.dimension)return;
        ItemAstralCompass.setClientSearchStatus(ItemAstralCompass.STATUS_SEARCHING,selectedId);sendAction(ItemAstralCompass.ACT_SEARCH,selectedId);refreshButtons();
    }
    private void requestCatalogSync(){if(toolReady())sendAction(ItemAstralCompass.ACT_REQUEST_CATALOG,"");}
    private void sendAction(int action,String id){AMNetHandler.INSTANCE.sendPacketToServer(AMPacketIDs.ASTRAL_COMPASS_ACTION,new AMDataWriter().add(heldSlot).add(action).add(id).generate());}
    @Override protected void keyTyped(char key,int code){if(searchField.textboxKeyTyped(key,code)){rebuildFilter(true);return;}if(code==Keyboard.KEY_RETURN||code==Keyboard.KEY_NUMPADENTER){beginSearch();return;}super.keyTyped(key,code);}
    @Override protected void mouseClicked(int x,int y,int mouse){
        searchField.mouseClicked(x,y,mouse);
        if(mouse==1&&x>=left+16&&x<left+panelWidth-16&&y>=listTop&&y<listTop+rows*32){int idx=page*rows+(y-listTop)/32;if(idx<filtered.size()){prefs.toggle(filtered.get(idx).id);rebuildFilter(false);}return;}
        super.mouseClicked(x,y,mouse);
    }
    @Override public void handleMouseInput(){super.handleMouseInput();int wheel=Mouse.getEventDWheel();if(wheel!=0){page=Math.max(0,Math.min(page+(wheel>0?-1:1),Math.max(0,(filtered.size()-1)/rows)));refreshButtons();}}
    private void line(String text,int y,int color){fontRendererObj.drawStringWithShadow(fontRendererObj.trimStringToWidth(text,panelWidth-36),left+18,y,color);}
    @Override public void drawScreen(int x,int y,float partial){
        drawWorldBackground(0);ArcaneChalkStyle.panel(left,top,panelWidth,panelHeight);ArcaneChalkStyle.seal(mc,11,left+32,top+24);
        fontRendererObj.drawStringWithShadow(text("title"),left+58,top+9,0xf0f6ff);
        ExtendedProperties p=ExtendedProperties.For(player);int level=p==null?0:p.getMagicLevel();int mana=p==null?0:(int)(p.getCurrentMana()+p.getBonusCurrentMana());
        fontRendererObj.drawStringWithShadow(text("req_status",level,50,mana,20000),left+58,top+25,canAffordSearch()?0x78e2ba:0xff929f);
        Gui.drawRect(left+16,top+88,left+panelWidth-16,top+108,0xff294d74);Gui.drawRect(left+17,top+89,left+panelWidth-17,top+107,0xff081526);searchField.drawTextBox();
        if(filtered.isEmpty())line(text("no_results"),listTop+10,0x8ea7c4);
        fontRendererObj.drawStringWithShadow((page+1)+"/"+Math.max(1,(filtered.size()+rows-1)/rows),left+74,footer+5,0x99b6d4);
        ItemAstralCompass.ClientSearchStatus state=ItemAstralCompass.getClientSearchStatus();
        line(text("status_"+state.status),footer+50,state.status==1?0x77caff:state.status==2?0x78e2ba:0xffce86);
        ItemAstralCompass.TargetData target=ItemAstralCompass.getTarget(player.getCurrentEquippedItem());
        String active=target==null?text("no_active_target"):text("active_short",new StructureTargetInfo(target.id,target.name,"modded",target.dimension,true,0).getDisplayName(),dim(target.dimension));
        line(active,footer+62,0xa1e5ff);
        StructureTargetInfo entry=selected();String hint=selectedVisit!=null?text("history_coords",selectedVisit.coordinates(),dim(selectedVisit.dimension)):entry==null?text("select_hint"):text("search_dimension",entry.getModName(),dim(entry.preferredDimension));
        line(hint,footer+74,0x88a3c4);super.drawScreen(x,y,partial);
        for(Object raw:buttonList){GuiButton b=(GuiButton)raw;if(x>=b.xPosition&&x<b.xPosition+b.width&&y>=b.yPosition&&y<b.yPosition+b.height){
            List<String> tip=new ArrayList<String>();
            if(b instanceof StructureRow){StructureRow row=(StructureRow)b;tip.add(row.entry.getDisplayName());tip.add(row.entry.getModName());tip.add(text("search_dimension_only",dim(row.entry.preferredDimension)));if(row.visit!=null)tip.add(row.visit.coordinates());tip.add(text("favorite_hint"));}
            else if(b.id==6)tip.add(text("hud_settings"));else if(b.id==8||b.id==9)tip.add(b.displayString);
            if(!tip.isEmpty())drawHoveringText(tip,x,y,fontRendererObj);break;
        }}
    }
    @Override public boolean doesGuiPauseGame(){return false;}
    private static final class StructureRow extends GuiButton {
        final StructureTargetInfo entry;final boolean selected,favorite;final Visit visit;
        StructureRow(int id,int x,int y,int w,StructureTargetInfo entry,boolean selected,boolean favorite,Visit visit){super(id,x,y,w,30,entry.getDisplayName());this.entry=entry;this.selected=selected;this.favorite=favorite;this.visit=visit;}
        @Override public void drawButton(Minecraft mc,int x,int y){
            if(!visible)return;boolean hover=x>=xPosition&&x<xPosition+width&&y>=yPosition&&y<yPosition+height;
            Gui.drawRect(xPosition,yPosition,xPosition+width,yPosition+height,selected?0xff67ccff:hover?0xff428dcc:0xff294d74);
            Gui.drawRect(xPosition+1,yPosition+1,xPosition+width-1,yPosition+height-1,selected?0xff143654:hover?0xff102c45:0xff0a192d);
            ArcaneChalkStyle.glyph(mc,entry.glyphIndex,xPosition+8,yPosition+8,14,0x77caff,1);
            mc.fontRenderer.drawStringWithShadow(mc.fontRenderer.trimStringToWidth(entry.getDisplayName(),width-50),xPosition+30,yPosition+5,0xecf7ff);
            String subtitle=entry.getModName()+" | "+dim(entry.preferredDimension);
            mc.fontRenderer.drawStringWithShadow(mc.fontRenderer.trimStringToWidth(subtitle,width-42),xPosition+30,yPosition+17,0x8daac3);
            if(favorite)mc.fontRenderer.drawStringWithShadow("*",xPosition+width-12,yPosition+5,0xffd77a);
        }
    }
}
