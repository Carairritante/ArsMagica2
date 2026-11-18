package am2.guis;

import am2.compass.*;
import am2.compass.client.AstralCompassPreferences;
import am2.compass.client.AstralCompassPreferences.Visit;
import am2.compass.client.StructurePreviewRegistry;
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
    private static final String[] CATEGORIES={"all","favorites","history"};
    private final EntityPlayer player;
    private final int heldSlot;
    private GuiTextField searchField;
    private String category="all", selectedId=AstralStructureLocator.ID_STRONGHOLD, modFilter="";
    private Integer dimensionFilter;
    private List<StructureTargetInfo> filtered=new ArrayList<StructureTargetInfo>();
    private List<Visit> historyRows=new ArrayList<Visit>();
    private Visit selectedVisit;
    private int page, rows, left, top, panelWidth, panelHeight, listTop, footer, lastCatalogHash, ticks;
    private int listWidth, previewX, previewW, previewH;
    private int previewImgX, previewImgY, previewImgSize;
    private boolean expandedPreview;
    private String lastClickedRowId="";
    private long lastRowClickMs;
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
        panelWidth=Math.min(620,width-16); panelHeight=203+rows*32;
        left=(width-panelWidth)/2; top=Math.max(2,(height-panelHeight)/2);
        listTop=top+112; footer=listTop+rows*32+5;
        int innerW=panelWidth-32;
        if(innerW>=388 && rows>=2) {
            listWidth=(innerW-8)*56/100;
            previewX=left+16+listWidth+8;
            previewW=innerW-listWidth-8;
            previewH=rows*32-2;
        } else {
            listWidth=innerW;
            previewX=0;
            previewW=0;
            previewH=0;
        }
        String query=searchField==null?"":searchField.getText();
        searchField=new GuiTextField(fontRendererObj,left+20,top+91,Math.max(40,panelWidth-174),16);
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
        GuiButton button=new GuiInstrumentButton(id,x,y,w,h,label);button.enabled=enabled;buttonList.add(button);return button;
    }
    private boolean busy() { return ItemAstralCompass.getClientSearchStatus().status==ItemAstralCompass.STATUS_SEARCHING; }
    @SuppressWarnings("unchecked") private void refreshButtons() {
        buttonList.clear();int tabGap=4;int tabW=(panelWidth-32-(CATEGORIES.length-1)*tabGap)/CATEGORIES.length;
        for(int i=0;i<CATEGORIES.length;i++)buttonList.add(new GuiInstrumentButton(20+i,left+16+i*(tabW+tabGap),top+44,tabW,18,
                text("tab_"+CATEGORIES[i]),-1,CATEGORIES[i].equals(category)));
        int half=(panelWidth-36)/2;
        String mod="";for(StructureTargetInfo info:ItemAstralCompass.getClientCatalog())if(info.getModId().equals(modFilter)){mod=info.getModName();break;}
        button(8,left+16,top+66,half,18,text("mod_filter",modFilter.isEmpty()?text("all"):mod),true);
        button(9,left+20+half,top+66,half,18,text("dimension_filter",dimensionFilter==null?text("all"):dim(dimensionFilter)),true);
        button(10,left+panelWidth-140,top+88,124,20,ItemAstralCompass.getClientSearchProtocol()>=2?text("radius",prefs.searchRadius/1000):text("server_range"),!busy()&&ItemAstralCompass.getClientSearchProtocol()>=2);
        for(int i=0;i<rows;i++) {int idx=page*rows+i;if(idx>=filtered.size())break;
            StructureTargetInfo entry=filtered.get(idx);buttonList.add(new StructureRow(100+idx,left+16,listTop+i*32,listWidth,entry,
                    entry.id.equals(selectedId),prefs.favorite(entry.id),"history".equals(category)?historyRows.get(idx):null));
        }
        button(1,left+16,footer,24,18,"<",page>0);button(2,left+44,footer,24,18,">",(page+1)*rows<filtered.size());
        int zoomW=24;
        int favX=left+112;
        int favW=Math.max(48,panelWidth-246-zoomW-4);
        button(7,favX,footer,favW,18,text(prefs.favorite(selectedId)?"unfavorite":"favorite"),selected()!=null);
        button(12,left+panelWidth-126-zoomW-4,footer,zoomW,18,"+",selected()!=null);
        buttonList.add(new GuiInstrumentButton(6,left+panelWidth-126,footer,24,18,"",GuiInstrumentButton.GEAR,false));
        button(3,left+panelWidth-98,footer,82,18,text("done"),true);
        int actionW=(panelWidth-40)/3; StructureTargetInfo entry=selected();
        boolean rightDimension=entry!=null&&(!entry.dimensionRestricted||entry.preferredDimension==player.dimension);
        button(4,left+16,footer+23,actionW,22,busy()?text("cancel"):text("divinate",20),toolReady()&&(busy()||entry!=null&&rightDimension&&canAffordSearch()));
        ItemAstralCompass.TargetData current=ItemAstralCompass.getTarget(player.getCurrentEquippedItem());
        boolean canNext=ItemAstralCompass.getClientSearchProtocol()>=2&&current!=null&&current.dimension==player.dimension&&current.id.equals(selectedId)&&!AstralStructureLocator.ID_WITCHWOOD_FOREST.equals(selectedId);
        button(11,left+20+actionW,footer+23,actionW,22,text("next_structure"),toolReady()&&!busy()&&canNext&&canAffordSearch());
        button(5,left+24+actionW*2,footer+23,actionW,22,text("clear"),toolReady()&&!busy()&&current!=null);
    }
    private boolean canAffordSearch() {ExtendedProperties p=ExtendedProperties.For(player);return p!=null&&AstralStructureLocator.hasRequiredLevel(p.getMagicLevel())&&AstralStructureLocator.hasEnoughMana(p.getCurrentMana(),p.getBonusCurrentMana());}
    private boolean toolReady() {ItemStack stack=player.getCurrentEquippedItem();return player.inventory.currentItem==heldSlot&&stack!=null&&stack.getItem() instanceof ItemAstralCompass;}
    @Override protected void actionPerformed(GuiButton button) {
        if(button.id>=20&&button.id<26){category=CATEGORIES[button.id-20];selectedVisit=null;rebuildFilter(true);return;}
        if(button.id>=100){
            int idx=button.id-100;
            if(idx<filtered.size()){
                String clickedId=filtered.get(idx).id;
                long now=Minecraft.getSystemTime();
                boolean sameRow=clickedId.equals(selectedId) && clickedId.equals(lastClickedRowId) && (now-lastRowClickMs)<=380L;
                selectedId=clickedId;
                selectedVisit="history".equals(category)?historyRows.get(idx):null;
                lastClickedRowId=clickedId;
                lastRowClickMs=now;
                refreshButtons();
                if(sameRow) expandedPreview=true;
            }
            return;
        }
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
            case 10:prefs.searchRadius=prefs.searchRadius==2000?5000:prefs.searchRadius==5000?10000:2000;prefs.save();refreshButtons();break;
            case 11:beginSearch(true);break;
            case 12:if(selected()!=null) expandedPreview=!expandedPreview;break;
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
    private void beginSearch() {beginSearch(false);}
    private void beginSearch(boolean next) {
        StructureTargetInfo entry=selected();if(!toolReady()||busy()||!canAffordSearch()||entry==null||entry.dimensionRestricted&&entry.preferredDimension!=player.dimension)return;
        if(ItemAstralCompass.getClientSearchProtocol()<2) {
            if(next)return;
            if(ItemAstralCompass.getClientSearchProtocol()>=1)ItemAstralCompass.setClientSearchStatus(ItemAstralCompass.STATUS_SEARCHING,selectedId);
            sendAction(ItemAstralCompass.ACT_SEARCH,selectedId);
            if(ItemAstralCompass.getClientSearchProtocol()<1){mc.displayGuiScreen(null);mc.setIngameFocus();}
            else refreshButtons();
            return;
        }
        ItemAstralCompass.setClientSearchStatus(ItemAstralCompass.STATUS_SEARCHING,selectedId);
        AMNetHandler.INSTANCE.sendPacketToServer(AMPacketIDs.ASTRAL_COMPASS_ACTION,new AMDataWriter().add(heldSlot).add(ItemAstralCompass.ACT_SEARCH).add(selectedId).add(prefs.searchRadius).add(next).generate());refreshButtons();
    }
    private void requestCatalogSync(){if(toolReady())sendAction(ItemAstralCompass.ACT_REQUEST_CATALOG,"");}
    private void sendAction(int action,String id){AMNetHandler.INSTANCE.sendPacketToServer(AMPacketIDs.ASTRAL_COMPASS_ACTION,new AMDataWriter().add(heldSlot).add(action).add(id).generate());}
    @Override protected void keyTyped(char key,int code){
        if(expandedPreview){
            if(code==Keyboard.KEY_ESCAPE||code==Keyboard.KEY_RETURN||code==Keyboard.KEY_NUMPADENTER||code==Keyboard.KEY_SPACE){
                expandedPreview=false;
            }
            return;
        }
        if(searchField.textboxKeyTyped(key,code)){rebuildFilter(true);return;}
        if(code==Keyboard.KEY_RETURN||code==Keyboard.KEY_NUMPADENTER){beginSearch();return;}
        super.keyTyped(key,code);
    }
    @Override protected void mouseClicked(int x,int y,int mouse){
        if(expandedPreview){
            expandedPreview=false;
            return;
        }
        searchField.mouseClicked(x,y,mouse);
        if(mouse==0 && previewW>0 && selected()!=null && x>=previewX && x<previewX+previewW && y>=listTop && y<listTop+previewH){
            expandedPreview=true;
            return;
        }
        if(mouse==1&&x>=left+16&&x<left+16+listWidth&&y>=listTop&&y<listTop+rows*32){
            int idx=page*rows+(y-listTop)/32;
            if(idx<filtered.size()){prefs.toggle(filtered.get(idx).id);rebuildFilter(false);}
            return;
        }
        super.mouseClicked(x,y,mouse);
    }
    @Override public void handleMouseInput(){
        super.handleMouseInput();
        if(expandedPreview)return;
        int wheel=Mouse.getEventDWheel();
        if(wheel!=0){page=Math.max(0,Math.min(page+(wheel>0?-1:1),Math.max(0,(filtered.size()-1)/rows)));refreshButtons();}
    }
    private void line(String text,int y,int color){fontRendererObj.drawStringWithShadow(fontRendererObj.trimStringToWidth(text,panelWidth-36),left+18,y,color);}
    @Override public void drawScreen(int x,int y,float partial){
        drawWorldBackground(0);InstrumentStyle.panel(left,top,panelWidth,panelHeight,false);InstrumentStyle.icon(false,left+18,top+9,30);
        fontRendererObj.drawStringWithShadow(text("title"),left+58,top+9,0xf0f6ff);
        ExtendedProperties p=ExtendedProperties.For(player);int level=p==null?0:p.getMagicLevel();int mana=p==null?0:(int)(p.getCurrentMana()+p.getBonusCurrentMana());
        fontRendererObj.drawStringWithShadow(text("req_status",level,50,mana,20000),left+58,top+25,canAffordSearch()?0x78e2ba:0xff929f);
        Gui.drawRect(left+16,top+88,left+panelWidth-144,top+108,0xff294d74);Gui.drawRect(left+17,top+89,left+panelWidth-145,top+107,0xff081526);searchField.drawTextBox();
        if(filtered.isEmpty())line(text("no_results"),listTop+10,0x8ea7c4);
        fontRendererObj.drawStringWithShadow((page+1)+"/"+Math.max(1,(filtered.size()+rows-1)/rows),left+74,footer+5,0x99b6d4);
        ItemAstralCompass.ClientSearchStatus state=ItemAstralCompass.getClientSearchStatus();
        line(statusText(state),footer+50,state.status==1?0x77caff:state.status==2?0x78e2ba:0xffce86);
        ItemAstralCompass.TargetData target=ItemAstralCompass.getTarget(player.getCurrentEquippedItem());
        String active=target==null?text("no_active_target"):text("active_short",new StructureTargetInfo(target.id,target.name,"modded",target.dimension,true,0).getDisplayName()+" ["+text("source_"+target.source)+"]",dim(target.dimension));
        line(active,footer+62,0xa1e5ff);
        StructureTargetInfo entry=selected();String hint=selectedVisit!=null?text("history_coords",selectedVisit.coordinates(),dim(selectedVisit.dimension)):entry==null?text("select_hint"):text("search_dimension",entry.getModName(),dim(entry.preferredDimension));
        line(hint,footer+74,0x88a3c4);
        super.drawScreen(x,y,partial);
        drawPreviewCard(entry,x,y);
        if(expandedPreview && entry!=null){
            drawExpandedPreviewModal(entry);
            return;
        }
        if(previewW>0 && entry!=null && x>=previewImgX && x<previewImgX+previewImgSize && y>=previewImgY && y<previewImgY+previewImgSize){
            List<String> tip=new ArrayList<String>();
            tip.add(entry.getDisplayName());
            tip.add(text("preview_expand_hint"));
            drawHoveringText(tip,x,y,fontRendererObj);
            return;
        }
        for(Object raw:buttonList){GuiButton b=(GuiButton)raw;if(x>=b.xPosition&&x<b.xPosition+b.width&&y>=b.yPosition&&y<b.yPosition+b.height){
            List<String> tip=new ArrayList<String>();
            if(b instanceof StructureRow){StructureRow row=(StructureRow)b;tip.add(row.entry.getDisplayName());tip.add(row.entry.getModName());tip.add(text("search_dimension_only",dim(row.entry.preferredDimension)));if(row.visit!=null){tip.add(row.visit.coordinates());tip.add(text("source_"+row.visit.source));}tip.add(text("favorite_hint"));tip.add(text("row_zoom_hint"));}
            else if(b.id==6)tip.add(text("hud_settings"));else if(b.id==10)tip.add(text(ItemAstralCompass.getClientSearchProtocol()>=2?"radius_hint":"update_server"));else if(b.id==11)tip.add(text(ItemAstralCompass.getClientSearchProtocol()>=2?"next_hint":"update_server"));else if(b.id==12)tip.add(text("preview_expand_hint"));else if(b.id==8||b.id==9)tip.add(b.displayString);
            if(!tip.isEmpty())drawHoveringText(tip,x,y,fontRendererObj);break;
        }}
    }
    private void drawPreviewCard(StructureTargetInfo entry,int mouseX,int mouseY){
        if(previewW<=0)return;
        boolean hoverCard=!expandedPreview && entry!=null && mouseX>=previewX && mouseX<previewX+previewW && mouseY>=listTop && mouseY<listTop+previewH;
        Gui.drawRect(previewX,listTop,previewX+previewW,listTop+previewH,hoverCard?0xff67ccff:0xff294d74);
        Gui.drawRect(previewX+1,listTop+1,previewX+previewW-1,listTop+previewH-1,0xff081526);
        if(entry==null){
            previewImgSize=0;
            String empty=fontRendererObj.trimStringToWidth(text("select_hint"),previewW-16);
            fontRendererObj.drawStringWithShadow(empty,previewX+8,listTop+10,0x8ea7c4);
            return;
        }
        int reservedTextH=previewH>=150?64:48;
        previewImgSize=Math.max(40,Math.min(previewW-16,previewH-reservedTextH));
        previewImgX=previewX+(previewW-previewImgSize)/2;
        previewImgY=listTop+4;
        Gui.drawRect(previewImgX-1,previewImgY-1,previewImgX+previewImgSize+1,previewImgY+previewImgSize+1,hoverCard?0xff67ccff:0xff35658e);
        StructurePreviewRegistry.drawPreview(mc,entry.id,previewImgX,previewImgY,previewImgSize,previewImgSize);

        String zoomBadge=text("zoom_badge");
        int badgeW=fontRendererObj.getStringWidth(zoomBadge)+8;
        int bx=previewImgX+previewImgSize-badgeW-2;
        int by=previewImgY+previewImgSize-12;
        Gui.drawRect(bx,by,bx+badgeW,by+11,0xc0061220);
        Gui.drawRect(bx,by,bx+badgeW,by+1,0xff64d6ee);
        fontRendererObj.drawStringWithShadow(zoomBadge,bx+4,by+2,0xa8f1ff);

        int textY=previewImgY+previewImgSize+4;
        int maxTextW=previewW-14;
        fontRendererObj.drawStringWithShadow(fontRendererObj.trimStringToWidth(entry.getDisplayName(),maxTextW),previewX+7,textY,0xecf7ff);
        textY+=10;
        String meta=entry.getModName()+" | "+dim(entry.preferredDimension);
        fontRendererObj.drawStringWithShadow(fontRendererObj.trimStringToWidth(meta,maxTextW),previewX+7,textY,0x67ccff);
        textY+=10;
        String desc=StructurePreviewRegistry.getVisualDescription(entry);
        @SuppressWarnings("unchecked")
        List<String> lines=fontRendererObj.listFormattedStringToWidth(desc,maxTextW);
        int bottomLimit=listTop+previewH-9;
        for(int i=0;i<lines.size()&&textY<=bottomLimit;i++){
            fontRendererObj.drawStringWithShadow(lines.get(i),previewX+7,textY,0x9fb8cf);
            textY+=9;
        }
    }
    private void drawExpandedPreviewModal(StructureTargetInfo entry){
        drawGradientRect(0,0,width,height,0xc8030a14,0xdc030a14);
        int modalW=Math.min(340,width-24);
        String desc=StructurePreviewRegistry.getVisualDescription(entry);
        @SuppressWarnings("unchecked")
        List<String> descLines=fontRendererObj.listFormattedStringToWidth(desc,modalW-24);
        int textBlockH=42+Math.min(4,descLines.size())*10;
        int imgSize=Math.max(96,Math.min(256,Math.min(modalW-24,height-textBlockH-40)));
        int modalH=imgSize+textBlockH+24;
        int mx=(width-modalW)/2;
        int my=(height-modalH)/2;
        InstrumentStyle.panel(mx,my,modalW,modalH,false);
        int ix=mx+(modalW-imgSize)/2;
        int iy=my+12;
        Gui.drawRect(ix-2,iy-2,ix+imgSize+2,iy+imgSize+2,0xff64d6ee);
        Gui.drawRect(ix-1,iy-1,ix+imgSize+1,iy+imgSize+1,0xff061220);
        StructurePreviewRegistry.drawPreview(mc,entry.id,ix,iy,imgSize,imgSize);

        int ty=iy+imgSize+8;
        fontRendererObj.drawStringWithShadow(fontRendererObj.trimStringToWidth(entry.getDisplayName(),modalW-24),mx+12,ty,0xf0f6ff);
        ty+=11;
        fontRendererObj.drawStringWithShadow(fontRendererObj.trimStringToWidth(entry.getModName()+" | "+dim(entry.preferredDimension),modalW-24),mx+12,ty,0x67ccff);
        ty+=12;
        for(int i=0;i<descLines.size()&&i<4;i++){
            fontRendererObj.drawStringWithShadow(descLines.get(i),mx+12,ty,0xb6cde0);
            ty+=10;
        }
        String closeHint=fontRendererObj.trimStringToWidth(text("preview_close_hint"),modalW-24);
        fontRendererObj.drawStringWithShadow(closeHint,mx+(modalW-fontRendererObj.getStringWidth(closeHint))/2,my+modalH-13,0x78e2ba);
    }
    public static String statusText(ItemAstralCompass.ClientSearchStatus state) {
        return state.status==ItemAstralCompass.STATUS_SEARCHING?text("progress",text("phase_"+state.phase),state.samples):text("status_"+state.status);
    }
    @Override public boolean doesGuiPauseGame(){return false;}
    private static final class StructureRow extends GuiButton {
        final StructureTargetInfo entry;final boolean selected,favorite;final Visit visit;
        StructureRow(int id,int x,int y,int w,StructureTargetInfo entry,boolean selected,boolean favorite,Visit visit){super(id,x,y,w,30,entry.getDisplayName());this.entry=entry;this.selected=selected;this.favorite=favorite;this.visit=visit;}
        @Override public void drawButton(Minecraft mc,int x,int y){
            if(!visible)return;boolean hover=x>=xPosition&&x<xPosition+width&&y>=yPosition&&y<yPosition+height;
            Gui.drawRect(xPosition,yPosition,xPosition+width,yPosition+height,selected?0xff67ccff:hover?0xff428dcc:0xff294d74);
            Gui.drawRect(xPosition+1,yPosition+1,xPosition+width-1,yPosition+height-1,selected?0xff143654:hover?0xff102c45:0xff0a192d);
            Gui.drawRect(xPosition+3,yPosition+2,xPosition+29,yPosition+28,selected?0xff67ccff:0xff2e5980);
            StructurePreviewRegistry.drawPreview(mc,entry.id,xPosition+4,yPosition+3,24,24);
            mc.fontRenderer.drawStringWithShadow(mc.fontRenderer.trimStringToWidth(entry.getDisplayName(),width-48),xPosition+33,yPosition+5,0xecf7ff);
            String subtitle=entry.getModName()+" | "+dim(entry.preferredDimension);
            mc.fontRenderer.drawStringWithShadow(mc.fontRenderer.trimStringToWidth(subtitle,width-40),xPosition+33,yPosition+17,0x8daac3);
            if(favorite)mc.fontRenderer.drawStringWithShadow("*",xPosition+width-12,yPosition+5,0xffd77a);
        }
    }
}
