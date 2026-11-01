"""Verify complete Compendium localization without changing technical values."""
from pathlib import Path
from collections import Counter
import re,sys,xml.etree.ElementTree as E

def check(source,target):
 a=list(E.fromstring(source).iter());b=list(E.fromstring(target).iter());assert len(a)==len(b),'node count changed'
 entries=descs=0
 for en,pt in zip(a,b):
  assert en.tag==pt.tag,'node type changed'
  assert {k:v for k,v in en.attrib.items() if k!='zeroItemText'}=={k:v for k,v in pt.attrib.items() if k!='zeroItemText'},en.attrib
  if 'id' in en.attrib:entries+=1
  x=(en.text or '').strip();y=(pt.text or '').strip()
  if en.tag in ('name','desc'):
   if x:assert y,('empty translation',en.tag,x)
   if en.tag=='desc' and x:
    descs+=1;assert x!=y,('untranslated description',x)
    assert Counter(re.findall(r'#[0-9a-zA-Z]|![a-zA-Z]',x))==Counter(re.findall(r'#[0-9a-zA-Z]|![a-zA-Z]',y)),('format markers',x)
  else:assert x==y,('technical value changed',en.tag,x,y)
 print(f'PASS: {entries} entries, {descs} descriptions, IDs/recipes/format markers preserved')
 return entries,descs

if __name__=='__main__':
 root=Path(__file__).resolve().parents[2]
 folder=root/'src/main/resources/assets/arsmagica2/docs'
 check((folder/'ArcaneCompendium_en_US.xml').read_bytes(),(folder/'ArcaneCompendium_pt_BR.xml').read_bytes())
