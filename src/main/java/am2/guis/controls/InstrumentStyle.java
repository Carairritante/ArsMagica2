package am2.guis.controls;

import cpw.mods.fml.relauncher.*;
import net.minecraft.client.gui.Gui;
import org.lwjgl.opengl.GL11;

/** Vector instrument artwork, used at native inventory size and in the instrument screens. */
@SideOnly(Side.CLIENT)
public final class InstrumentStyle extends Gui {
    private static final InstrumentStyle PAINTER=new InstrumentStyle();
    private static final float[] SIN=new float[41],COS=new float[41];
    static{for(int i=0;i<=40;i++){SIN[i]=(float)Math.sin(i*Math.PI/20);COS[i]=(float)Math.cos(i*Math.PI/20);}}
    private InstrumentStyle(){}
    public static void panel(int x,int y,int w,int h,boolean striker){
        int accent=striker?0xffd4b077:0xff64d6ee;
        drawRect(x,y,x+w,y+h,0xf0061220);
        drawRect(x+1,y+1,x+w-1,y+(h<=110?30:39),0xff102b40);
        drawRect(x,y,x+w,y+2,accent);drawRect(x,y+h-1,x+w,y+h,0xff36546d);
        drawRect(x,y+2,x+1,y+h-1,0xff36546d);drawRect(x+w-1,y+2,x+w,y+h-1,0xff36546d);
        drawRect(x+8,y+8,x+10,y+31,accent);
        drawRect(x+w-38,y+8,x+w-12,y+10,accent);
        int separator=h<=110?33:43;drawRect(x+12,y+separator,x+w-12,y+separator+1,0xff29475e);
        for(int i=0;i<4;i++){int px=i%2==0?x+5:x+w-7,py=i<2?y+5:y+h-7;drawRect(px,py,px+2,py+2,0xff8a9ba7);}
    }
    private static void color(int rgb){GL11.glColor4f(((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f,1);}
    private static void triangle(float ax,float ay,float bx,float by,float cx,float cy,int rgb){color(rgb);GL11.glBegin(GL11.GL_TRIANGLES);GL11.glVertex2f(ax,ay);GL11.glVertex2f(bx,by);GL11.glVertex2f(cx,cy);GL11.glEnd();}
    private static void ring(float radius,float inner,int rgb){color(rgb);GL11.glBegin(GL11.GL_TRIANGLE_STRIP);for(int i=0;i<=40;i++){GL11.glVertex2f(8+SIN[i]*radius,8-COS[i]*radius);GL11.glVertex2f(8+SIN[i]*inner,8-COS[i]*inner);}GL11.glEnd();}
    public static void icon(boolean striker,int x,int y,int size){
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glPushMatrix();
        try{
            GL11.glTranslatef(x,y,0);GL11.glScalef(size/16f,size/16f,1);
            GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            if(!striker){
                ring(7.5f,0,0x061422);ring(7.3f,6.2f,0xd8bd84);ring(6.1f,5.6f,0x56bdd4);
                for(int i=0;i<4;i++){GL11.glPushMatrix();GL11.glTranslatef(8,8,0);GL11.glRotatef(i*90,0,0,1);triangle(0,-5,-0.8f,-3,0.8f,-3,0xe4dbc1);GL11.glPopMatrix();}
                triangle(10.6f,2.7f,5.9f,8.3f,8.6f,9.6f,0x9df1ff);triangle(10.6f,2.7f,8.6f,9.6f,9.7f,7.7f,0x279abe);
                triangle(5.4f,13.3f,5.9f,8.3f,8.6f,9.6f,0xc09e6d);ring(1.1f,0,0xe5c98d);
            }else{
                triangle(1,12,8,9,15,12,0x42617a);triangle(1,12,15,12,8,15,0x152c43);
                drawRect(2,12,14,14,0xff18354e);drawRect(3,13,13,14,0xff59d3ee);
                drawRect(5,3,11,12,0xff071321);drawRect(5,3,6,12,0xffb49a71);drawRect(10,3,11,12,0xffa58a63);
                drawRect(7,4,9,11,0xff1f789c);drawRect(7,6,9,10,0xffa0f3ff);
                drawRect(4,2,12,4,0xffd5b77c);drawRect(6,1,10,2,0xfffbdf99);
                drawRect(6,10,10,12,0xffe6cd93);drawRect(1,9,4,12,0xff284e66);drawRect(12,9,15,12,0xff284e66);
            }
        }finally{GL11.glPopMatrix();GL11.glPopAttrib();}
    }
}
