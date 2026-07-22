const reduced=matchMedia('(prefers-reduced-motion: reduce)').matches;
const reveals=document.querySelectorAll('.reveal');
if('IntersectionObserver' in window){
  const io=new IntersectionObserver(entries=>entries.forEach(e=>{if(e.isIntersecting){e.target.classList.add('visible');io.unobserve(e.target)}}),{threshold:.13});
  reveals.forEach(el=>io.observe(el));
}else{
  reveals.forEach(el=>el.classList.add('visible'));
}

if(!reduced){
  const glow=document.querySelector('.cursor-glow');
  addEventListener('pointermove',e=>{glow.style.left=e.clientX+'px';glow.style.top=e.clientY+'px'});
  const tilt=document.querySelector('[data-tilt]');
  const stage=tilt?.parentElement;
  stage?.addEventListener('pointermove',e=>{
    const r=stage.getBoundingClientRect();
    const x=(e.clientX-r.left)/r.width-.5;
    const y=(e.clientY-r.top)/r.height-.5;
    tilt.style.transform=`rotateY(${x*10}deg) rotateX(${-y*8}deg) translateY(${y*5}px)`;
  });
  stage?.addEventListener('pointerleave',()=>tilt.style.transform='');
}

document.querySelectorAll('.check-demo input').forEach(input=>input.addEventListener('change',()=>{
  input.closest('label').animate([{transform:'scale(1)'},{transform:'scale(.98)'},{transform:'scale(1)'}],{duration:260});
}));

document.querySelectorAll('a[href^="#"]').forEach(a=>a.addEventListener('click',e=>{
  const target=document.querySelector(a.getAttribute('href'));
  if(target){e.preventDefault();target.scrollIntoView({behavior:reduced?'auto':'smooth'});}
}));
