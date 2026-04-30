class MidiClientApi {
  constructor() {
    this.baseUrl = location.protocol+'//'+location.host+'/client'
  }

  async getListenersIds() {
    const resp = await fetch(this.baseUrl+'/', {
      method: 'GET'
    })

    if( !resp.ok ) throw new Error('fail get listeners')
    const idList = await resp.json()
    return idList
  }

  listener(id) {
    const ls = new MidiClientListenerApi(id, this)
    if( this.serverTime ){
      ls.serverTime = this.serverTime
    }
    return ls
  }

  async getListeners() {
    const ids = await this.getListenersIds()
    const lss = {}
    for( const id of ids ){
      const ls = this.listener(id)
      lss[id] = ls
      await ls.updateState()
    }
    return lss
  }

  async getServerTime() {
    const resp = await fetch(this.baseUrl+'/time', {
      method: 'GET'
    })
    if( !resp.ok ) throw new Error('fail get listeners')
    const time = await resp.json()
    return time
  }

  async updateServerTime() {
    const t = await this.getServerTime()
    this.serverTime = t
    return t
  }

  /**
   * @param opts : Object
   * 
   * - `opts.timestampShift = "now"`
   * - `opts.timestampShift = nanoSecOffset : number`
   */
  async newListener() {
    let opts = {}
    if( typeof(arguments[0])=="object" ){
      opts = arguments[0];
    }

    let url = this.baseUrl+'/new'
    let qs = []
    if( opts.timestampShift==='now' ){
      qs.push(`timestamp-shift=now`)
    }else if( typeof(opts.timestampShift)=="number" ){
      qs.push(`timestamp-shift=${opts.timestampShift}`)
    }

    if( qs.length>0 ){
      url = url + "?" + qs.join('&')
    }

    const resp = await fetch(url, {
      method: 'POST'
    })
    if( !resp.ok ) throw new Error('fail create new client')

    const jsn = await resp.json()    
    const ls = new MidiClientListenerApi(jsn.id, this)

    ls.createTime = {}
    if( typeof(jsn.millis)=="number" )ls.createTime.millis = jsn.millis
    if( typeof(jsn.nano)=="number" )ls.createTime.nano = jsn.nano
    if( typeof(jsn.time)=="string" )ls.createTime.time = jsn.time

    return ls
  }
}

class MidiClientListenerApi {
  constructor(id, api) {
    this.id = id
    this.api = api
    this.removeAfterRead = true
    this.waitIfEmptyMs = 3000
  }

  async close(){
    const baseUrl = this.api.baseUrl
    const url = baseUrl + '/' + this.id
    const resp = await fetch(url, {
      method: 'DELETE'
    })
    if( !resp.ok ) throw new Error('fail delete of listener id='+this.id)
    
    const jsn = await resp.json()
    return jsn
  }

  async getState(){
    const baseUrl = this.api.baseUrl
    const url = baseUrl + '/' + this.id
    const resp = await fetch(url)
    if( !resp.ok ) throw new Error('fail getState of listener id='+this.id)
    
    const jsn = await resp.json()
    return jsn
  }

  async updateState(){
    const state = await this.getState()
    this.events = state.events
    this.eventsQueuesCount = state.eventsQueuesCount
    this.rawEvents = state.rawEvents
    this.rawEventsQueuesCount = state.rawEventsQueuesCount
    this.timestampShift = state.timestampShift ? state.timestampShift : 0
    this.timeShiftMillis = state.timeShiftMillis ? state.timeShiftMillis : 0
    return this
  }

  async getEvents() {
    const baseUrl = this.api.baseUrl
    let url = baseUrl + '/' + this.id + '/events'
    let qs = []

    if( this.removeAfterRead ){
      qs.push('remove-after-read=1')
    }

    if( this.waitIfEmptyMs>0 ){
      qs.push(`wait-if-empty-ms=${this.waitIfEmptyMs}`)
    }

    if( qs.length>0 ){
      url = url + "?" + qs.join('&')
    }

    const resp = await fetch(url)
    if( !resp.ok ) throw new Error('fail create new client')

    const jsn = await resp.json()
    return jsn
  }
}