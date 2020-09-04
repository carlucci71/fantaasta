package com.daniele.asta;

import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.daniele.asta.dto.GiocatoriPerRuolo;
import com.daniele.asta.dto.GiocatoriPerSquadra;
import com.daniele.asta.dto.SpesoPerRuolo;
import com.daniele.asta.entity.Allenatori;
import com.daniele.asta.entity.Configurazione;
import com.daniele.asta.entity.Fantarose;
import com.daniele.asta.entity.Giocatori;
import com.daniele.asta.entity.LoggerMessaggi;
import com.daniele.asta.repository.AllenatoriRepository;
import com.daniele.asta.repository.ConfigurazioneRepository;
import com.daniele.asta.repository.FantaroseRepository;
import com.daniele.asta.repository.GiocatoriRepository;
import com.daniele.asta.repository.LoggerRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@RestController
@RequestMapping({ "/" })
public class MyController {

	@Autowired Environment environment;	
	@Autowired HttpSession httpSession;
	@Autowired AllenatoriRepository allenatoriRepository;
	@Autowired FantaroseRepository fantaroseRepository;
	@Autowired GiocatoriRepository giocatoriRepository;
	@Autowired LoggerRepository loggerRepository;
	@Autowired ConfigurazioneRepository configurazioneRepository;
	@Autowired Criptaggio criptaggio; 
	@Autowired EntityManager em;
	@Autowired SocketHandler socketHandler;
	private String turno="0";
	private String nomeGiocatoreTurno="";
	private Boolean isATurni;

	@RequestMapping("/init")
	public Map<String, Object> init() {
		Map<String, Object> ret = new HashMap<>();
		Configurazione configurazione = getConfigurazione();
		if (configurazione.getNumeroGiocatori()==null) {
			ret.put("DA_CONFIGURARE", "x");
		}
		else {
			String giocatoreLoggato = (String) httpSession.getAttribute("nomeGiocatoreLoggato");
			String idLoggato = (String) httpSession.getAttribute("idLoggato");
			if (giocatoreLoggato != null) {
				ret.put("giocatoreLoggato", giocatoreLoggato);
				ret.put("idLoggato", idLoggato);
			}
			Iterable<Allenatori> allAllenatori = getAllAllenatori();
			for (Allenatori allenatori : allAllenatori) {
				if(allenatori.getOrdine()==Integer.parseInt(getTurno())) {
					setNomeGiocatoreTurno(allenatori.getNome());
				}
			}
			isATurni = configurazione.getIsATurni();
			if(isATurni) {
				ret.put("isATurni", "S");
			}
			else {
				ret.put("isATurni", "N");
			}
			ret.put("elencoAllenatori", allAllenatori);
			ret.put("nomeGiocatoreTurno", getNomeGiocatoreTurno());
			ret.put("turno", getTurno());
		}
		return ret;
	}
	@PostMapping("/caricaFile")
	public Map<String, Object> caricaFile(@RequestBody Map<String,Object> body) throws Exception {
		Map<String,Object> ret = new HashMap<>();
		if(isOkDispositiva(body)) {
			String content = (String) body.get("file");
			String tipoFile = (String) body.get("tipo");
			giocatoriRepository.deleteAll();
			if("FS".equalsIgnoreCase(tipoFile)) {
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				DocumentBuilder builder = factory.newDocumentBuilder();
				InputSource is = new InputSource(new StringReader(content));
				Document parse = builder.parse(is);
				NodeList childNodes = parse.getChildNodes().item(0).getChildNodes();
				for (int i=0;i<childNodes.getLength();i++) {
					if (i>0) {
						Node tr = childNodes.item(i);
						NodeList childNodesTr = tr.getChildNodes();
						String id = childNodesTr.item(0).getTextContent(); 
						String squadra = childNodesTr.item(3).getTextContent(); 
						String nome = childNodesTr.item(1).getTextContent() + " " + childNodesTr.item(2).getTextContent(); 
						String ruolo = childNodesTr.item(4).getTextContent(); 
						String quotazione = childNodesTr.item(6).getTextContent(); 
						Giocatori giocatori = new Giocatori();
						giocatori.setId(Integer.parseInt(id));
						giocatori.setNome(nome);
						giocatori.setQuotazione(Integer.parseInt(quotazione));
						giocatori.setRuolo(ruolo);
						giocatori.setSquadra(squadra);
						giocatoriRepository.save(giocatori);
					}
				}
			}
			else if("MANTRA".equalsIgnoreCase(tipoFile)) {
				String[] split = content.split("\n");
				for(int i=1;i<split.length;i++) {
					String riga = split[i];
					String[] colonne = riga.split("\t");
					Giocatori giocatori = new Giocatori();
					giocatori.setId(Integer.parseInt(colonne[0]));
					giocatori.setNome(colonne[2]);
					try
					{
						giocatori.setQuotazione(Integer.parseInt(colonne[6].replace("\r", "")));
					}
					catch (Exception e)
					{	
						giocatori.setQuotazione(-1);
					}
					giocatori.setRuolo(colonne[1].replaceAll("\"", ""));
					giocatori.setSquadra(colonne[3]);
					giocatoriRepository.save(giocatori);
				}
			}
			else {
				throw new RuntimeException("Tipo file non riconoscituo:" + tipoFile);
			}
			socketHandler.notificaCaricaFile();
			ret.put("esitoDispositiva", "OK");
		}
		else {
			ret.put("esitoDispositiva", "KO");
		}
		
		return ret;

	}
	
	private boolean isOkDispositiva(@RequestBody Map<String, Object> body) {
		try {
			Integer tokenDispositiva = (Integer) body.get("tokenDispositiva");
			String idgiocatore =  null;
			idgiocatore=body.get("idgiocatore").toString();
			socketHandler.verificaTokenDispositiva(idgiocatore);
			long timeout=0;
			Integer tokenVerifica = socketHandler.getTokenVerifica();
			while(tokenVerifica<0 && timeout<2000) {
				tokenVerifica = socketHandler.getTokenVerifica();
				timeout=timeout+100;
				Thread.currentThread().sleep(100);
			}
			socketHandler.setTokenVerifica(-1);
			if(tokenVerifica<0) return false;
			return tokenDispositiva.equals(tokenVerifica);
		} catch (Exception e) {
			return false;
		}
	}
	
	@PostMapping("/cancellaOfferta")
	public Map<String,Object>  cancellaOfferta(@RequestBody Map<String, Object> body) throws Exception {
		Map<String,Object> ret = new HashMap<>();
		if(isOkDispositiva(body)) {
			Map<String, Object> mapOfferta = (Map)body.get("offerta");
			Integer idGiocatore=(Integer) mapOfferta.get("idGiocatore");
			fantaroseRepository.delete(idGiocatore);
			socketHandler.notificaCancellaOfferta(mapOfferta);
			ret.put("ret", elencoCronologiaOfferte());
			ret.put("esitoDispositiva", "OK");
		}
		else {
			ret.put("esitoDispositiva", "KO");
		}
		return ret;
	}

	@PostMapping("/azzera")
	public Map<String,Object> azzera(@RequestBody Map<String, Object> body) throws Exception {
		Map<String,Object> ret = new HashMap<>();
		if(isOkDispositiva(body)) {
			ret.put("esitoDispositiva", "OK");
			if (body.get("conferma") != null && body.get("conferma").toString().equalsIgnoreCase("S")) {
				giocatoriRepository.deleteAll();
				fantaroseRepository.deleteAll();
				allenatoriRepository.deleteAll();
				Configurazione configurazione = getConfigurazione();
				configurazione.setNumeroGiocatori(null);
				configurazioneRepository.save(configurazione);
			}
		}
		else {
			ret.put("esitoDispositiva", "KO");
		}
		return ret;
	}
	@PostMapping("/inizializzaLega")
	public Map<String, Object> inizializzaLega(@RequestBody Map<String, Object> body) throws Exception {
		Configurazione configurazione = getConfigurazione();
		Map<String,Object> ret = new HashMap<>();
		if(configurazione.getNumeroGiocatori() == null) {
			Integer numUtenti=(Integer) body.get("numUtenti");
			isATurni=(Boolean) body.get("isATurni");
			configurazione.setNumeroGiocatori(numUtenti);
			configurazione.setIsATurni(isATurni);
			configurazioneRepository.save(configurazione);
			for(int i=0;i<numUtenti;i++) {
				Allenatori al = new Allenatori();
				al.setId(i);
				al.setOrdine(i);
				if (i==0) {
					al.setIsAdmin(true);
					String giocatoreLoggato = (String) httpSession.getAttribute("nomeGiocatoreLoggato");
					if(giocatoreLoggato==null) {
						al.setNome("GIOC0");
					}
					else {
						al.setNome(giocatoreLoggato);
					}
				}
				else {
					al.setIsAdmin(false);
					al.setNome("GIOC"+i);
				}
				al.setPwd("");
				allenatoriRepository.save(al);
				socketHandler.notificaInizializzaLega();
			}
			ret.put("esitoDispositiva", "OK");
		}
		else {
			ret.put("esitoDispositiva", "KO");
		}
		return ret;
	}
	@PostMapping("aggiornaSessioneNomeUtente")
	public void aggiornaSessioneNomeUtente(@RequestBody Map<String, Object> body) {
		httpSession.setAttribute("nomeGiocatoreLoggato", (String)body.get("nuovoNome"));
	}
	@PostMapping("cancellaSessioneNomeUtente")
	public  Map<String,Object>   cancellaSessioneNomeUtente() {
		httpSession.removeAttribute("nomeGiocatoreLoggato");
		httpSession.removeAttribute("idLoggato");
		Map<String,Object>  ret = new HashMap<>();
		ret.put("esito", "OK");
		return ret;
	}
	@PostMapping("/aggiornaConfigLega")
	public  Map<String,Object>  aggiornaConfigLega(@RequestBody Map<String, Object> body) throws Exception {
		Map <String, Object> ret = new HashMap<>();
		if(isOkDispositiva(body)) {
			Map <String, String> utentiRinominati = new HashMap<>();
			int i=0;
			Boolean admin = (Boolean) body.get("admin");
			isATurni = (Boolean) body.get("isATurni");
			List<Map<String, Object>> elencoAllenatori = (List<Map<String, Object>>) body.get("elencoAllenatori");
			for (Map<String, Object> map : elencoAllenatori) {
				Allenatori al = allenatoriRepository.findOne((Integer) map.get("id"));
				String nuovoNome = (String) map.get("nuovoNome");
				String vecchioNome=al.getNome();
				String giocatoreLoggato = (String) httpSession.getAttribute("nomeGiocatoreLoggato");
				if (!vecchioNome.equalsIgnoreCase(nuovoNome)) {
					utentiRinominati.put(vecchioNome, nuovoNome);
					if(giocatoreLoggato.equalsIgnoreCase(vecchioNome)) {
						ret.put("nuovoNomeLoggato", nuovoNome);
						ret.put("vecchioNomeLoggato", vecchioNome);
						//					httpSession.setAttribute("nomeGiocatoreLoggato", nuovoNome);
					}
				}
				al.setNome(nuovoNome);
				String pwd = (String) map.get("pwd");
				if (!pwd.equalsIgnoreCase(al.getPwd()))
					al.setPwd(criptaggio.encrypt(pwd,nuovoNome));
				if("true".equalsIgnoreCase(map.get("isAdmin").toString()))
					al.setIsAdmin(true);
				else
					al.setIsAdmin(false);
				if (admin) al.setOrdine((Integer) map.get("ordine"));
				i++;
				allenatoriRepository.save(al);
			}
			socketHandler.aggiornaConfigLega(utentiRinominati,getAllAllenatori());
			Configurazione configurazione = getConfigurazione();
			configurazione.setIsATurni(isATurni);
			configurazioneRepository.save(configurazione);
			if(isATurni) {
				ret.put("isATurni", "S");
			}
			else {
				ret.put("isATurni", "N");
			}
			ret.put("esitoDispositiva", "OK");
		}
		else {
			ret.put("esitoDispositiva", "KO");
		}
		return ret;
	}

	@GetMapping("/cripta")
	public Map<String,String> cripta(@RequestParam(name = "pwd") String pwd,@RequestParam(name = "key") String key) throws Exception {
		Map <String, String> m = new HashMap<>();
		m.put("value", criptaggio.encrypt(pwd, key));
		return m;
	}
/*
	@GetMapping("/decripta")
	public String decripta(@RequestParam(name = "pwd") String pwd,@RequestParam(name = "key") String key) throws Exception {
		return criptaggio.decrypt(pwd, key);
	}
*/	
	@PostMapping("/confermaAsta")
	public Map<String, Object> confermaAsta(@RequestBody Map<String, Object> body) throws Exception {
		Map<String,Object> ret = new HashMap<>();
		if(isOkDispositiva(body)) {
			String idgiocatore =  ((Map)body.get("offerta")).get("idgiocatore").toString();
			Integer offerta = (Integer) ((Map)body.get("offerta")).get("offerta");
			String idCalciatore = ((Map)body.get("offerta")).get("idCalciatore").toString();
			Calendar c = Calendar.getInstance();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
			String stm = sdf.format(c.getTime());
			Fantarose fantarosa = new Fantarose();
			fantarosa.setCosto(offerta);
			fantarosa.setIdAllenatore(Integer.parseInt(idgiocatore));
			fantarosa.setIdGiocatore(Integer.parseInt(idCalciatore));
			fantarosa.setSqlTime(stm);
			fantaroseRepository.save(fantarosa);
			ret.put("esitoDispositiva", "OK");
		}
		else {
			ret.put("esitoDispositiva", "KO");
		}
		return ret;
	}

	/*
	@RequestMapping("/x")
	public Iterable<Fantarose> x() {
		return fantaroseRepository.x();
	}
	*/

	@RequestMapping("/spesoAllenatori")
	public List<Map<String, Object>>  spesoAllenatori() {
		try {
			String sql = "select sum(costo) costo, a.nome from fantarose f, allenatori a where a.id = idAllenatore group by a.nome";
			Query qy = em.createNativeQuery(sql);
			List<Object[]> resultList = qy.getResultList();
			List<Map<String, Object>> ret = new ArrayList<>();
			for (Object[] row : resultList) {
				Map<String, Object> m = new HashMap<>();
				m.put("costo",  row[0]);
				m.put("nome",row[1]);
				ret.add(m);
			}
			return ret;
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	@RequestMapping("/contaGiocatoriPerRuolo")
	public List<GiocatoriPerRuolo>  contaGiocatoriPerRuolo() {
		return fantaroseRepository.contaGiocatoriPerRuolo();
	}

	
	private static final Map<String, String> ruoliOrdine = new HashMap<>();
    static {
    	ruoliOrdine.put("P", "1P");
    	ruoliOrdine.put("D", "2D");
    	ruoliOrdine.put("C", "3C");
    	ruoliOrdine.put("A", "4A");
    }	
	
	@RequestMapping("/giocatoriPerSquadra")
	public Map giocatoriPerSquadra() {
		Map<String, Map<String, Long>> mapSpesoPerRuolo = new TreeMap<>();
		Iterable<SpesoPerRuolo> spesoPerRuolo = fantaroseRepository.spesoPerRuolo();
		for (SpesoPerRuolo speso : spesoPerRuolo) {
			Map<String, Long> tmp=new TreeMap<>();
			tmp.put("speso", speso.getCosto());
			tmp.put("conta", speso.getConta());
			mapSpesoPerRuolo.put(speso.getNome(), tmp);
		}
		Iterable<GiocatoriPerSquadra> giocatoriPerSquadra = fantaroseRepository.giocatoriPerSquadra();
		Map<String, Map<String, Object>> ret = new TreeMap<>();
		for (GiocatoriPerSquadra giocatorePerSquadra : giocatoriPerSquadra) {
			String allenatore = giocatorePerSquadra.getAllenatore();
			Map<String, Long> spese = mapSpesoPerRuolo.get(allenatore);
			Map<String, List<String>> mapRuoli =null;
			if(ret.get(allenatore) != null)
				mapRuoli =(Map<String, List<String>>) ret.get(allenatore).get("ruoli");
			if(mapRuoli==null) {
				mapRuoli=new TreeMap<>();
			}
			String ruolo = ruoliOrdine.get(giocatorePerSquadra.getRuolo());
			List<String> list = (List<String>) mapRuoli.get(ruolo);
			if (list==null) {
				list=new ArrayList<>();
			}
			list.add(giocatorePerSquadra.getGiocatore() + " " + giocatorePerSquadra.getSquadra() + " " + giocatorePerSquadra.getCosto());
			mapRuoli.put(ruolo, list);
			Map<String, Object> t = new TreeMap<>();
			t.put("ruoli", mapRuoli);
			t.put("spese", spese);
			ret.put(allenatore, t);
		}
		return ret;
	}

	@RequestMapping("/spesoPerRuolo")
	public Iterable<SpesoPerRuolo>  spesoPerRuolo() {
		return fantaroseRepository.spesoPerRuolo();
	}
	
	@RequestMapping("/elencoCronologiaOfferte")
	public List<Map<String, Object>>  elencoCronologiaOfferte() {
		try {
			String sql = "select a.Nome allenatore, g.Squadra, g.Ruolo, g.nome giocatore, Costo, sqlTime, idGiocatore, idAllenatore   from  fantarose f, " + 
					"giocatori g, allenatori a  where g.id = idGiocatore and a.id = idAllenatore order by sqlTime desc";
			Query qy = em.createNativeQuery(sql);
			List<Object[]> resultList = qy.getResultList();
			List<Map<String, Object>> ret = new ArrayList<>();
			for (Object[] row : resultList) {
				Map<String, Object> m = new HashMap<>();
				m.put("allenatore",  row[0]);
				m.put("squadra", row[1]);
				m.put("ruolo",  row[2]);
				m.put("giocatore",  row[3]);
				m.put("costo",  row[4]);
				m.put("sqlTime",row[5]);
				m.put("idGiocatore",  row[6]);
				m.put("idAllenatore", row[7]);
				ret.add(m);
			}
			return ret;
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	@RequestMapping("/elencoOfferte")
	public List<Map<String, Object>>  elencoOfferte() {
		try {
			String sql = "select a.Nome allenatore, g.Squadra, g.Ruolo, g.nome giocatore, Costo, sqlTime from fantarose f, giocatori g, " + 
					"allenatori a where g.id = idGiocatore and a.id = idAllenatore order by allenatore, ruolo desc, giocatore";
			Query qy = em.createNativeQuery(sql);
			List<Object[]> resultList = qy.getResultList();
			List<Map<String, Object>> ret = new ArrayList<>();
			for (Object[] row : resultList) {
				Map<String, Object> m = new HashMap<>();
				m.put("allenatore", row[0]);
				m.put("squadra", row[1]);
				m.put("ruolo", row[2]);
				m.put("giocatore", row[3]);
				m.put("costo", row[4]);
				m.put("sqlTime", row[5]);
				ret.add(m);
			}
			return ret;
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	private ObjectMapper mapper = new ObjectMapper();
	public String toJson(Object o)
	{
		try
		{
			byte[] data = mapper.writeValueAsBytes(o);
			return new String(data);//, Charsets.ISO_8859_1
		} catch (JsonProcessingException e)
		{
			throw new RuntimeException(e);
		} 
	}
	public List<Map<String, Object>> jsonToList(String json)
	{
		try
		{
			return mapper.readValue(json, new TypeReference<List<Map<String, Object>>>(){});
		} catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
	@RequestMapping("/riepilogoAllenatori")
	public List<Map<String, Object>>  riepilogoAllenatori() {
		try {
			String sql = "select count(ruolo) conta, ruolo, a.nome nome from fantarose f, allenatori a, giocatori g where g.id=idGiocatore " + 
					"and a.id = idAllenatore group by a.nome ,ruolo order by a.nome, ruolo desc";
			Query qy = em.createNativeQuery(sql);
			List<Object[]> resultList = qy.getResultList();
			List<Map<String, Object>> ret = new ArrayList<>();
			for (Object[] row : resultList) {
				Map<String, Object> m = new HashMap<>();
				m.put("conta",  row[0]);
				m.put("ruolo",row[1]);
				m.put("nome",row[2]);
				ret.add(m);
			}
			return ret;
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}

//	@Cacheable(cacheNames = "allenatori")
	@GetMapping(path="/allAllenatori")
	public @ResponseBody Iterable<Allenatori> getAllAllenatori() {
		Iterable<Allenatori> findAll = allenatoriRepository.getAllenatoriOrderByOrdine();
		for (Allenatori allenatori : findAll) {
			allenatori.setNuovoNome(allenatori.getNome());
		}
		return findAll;
	}	

	@GetMapping(path="/allFantarose")
	public @ResponseBody Iterable<Fantarose> getAllFantarose() {
		return fantaroseRepository.findAll();
	}	

	@GetMapping(path="/allGiocatori")
	public @ResponseBody Iterable<Giocatori> getAllGiocatori() {
		return giocatoriRepository.findAll();
	}	
	@GetMapping(path="/elencoLoggerMessaggi")
	public @ResponseBody Iterable<LoggerMessaggi> elencoLoggerMessaggi() {
		return loggerRepository.findAll();
	}	
	
	@GetMapping(path="/configurazione")
	public @ResponseBody Configurazione getConfigurazione() {
		return configurazioneRepository.findOne(1);
	}	
	
	@GetMapping(path="/giocatoriLiberi")
	public @ResponseBody List<Map<String, Object>> getGiocatoriLiberi() {
//		Iterable<Giocatori> giocatoriLiberi = giocatoriRepository.getGiocatoriLiberi();
		List<Object[]> resultList = giocatoriRepository.getGiocatoriLiberi();
		List<Map<String, Object>> ret = new ArrayList<>();
//		for (Giocatori giocatori : giocatoriLiberi) {
//			System.out.println(giocatori);
//		}
		for (Object[] row : resultList) {
			Map<String, Object> m = new HashMap<>();
			m.put("id",  row[0]);
			m.put("squadra",  row[1]);
			m.put("nome",  row[2]);
			m.put("ruolo",  row[3]);
			m.put("quotazione",  row[4]);
			ret.add(m);
		}
		return ret;
	}
	public String getNomeGiocatoreTurno() {
		return nomeGiocatoreTurno;
	}
	public void setNomeGiocatoreTurno(String nomeGiocatoreTurno) {
		this.nomeGiocatoreTurno = nomeGiocatoreTurno;
	}
	public String getTurno() {
		return turno;
	}
	public void setTurno(String turno) {
		this.turno = turno;
	}
	public Boolean getIsATurni() {
		return isATurni;
	}
	public void setIsATurni(Boolean isATurni) {
		this.isATurni = isATurni;
	}

}
