class Timer {
    constructor(name){
        this.name = name;
        this.start = Date.now();
    }

    str(){
        return `timer '${this.name}': ${Date.now() - this.start} ms`;
    }
}
