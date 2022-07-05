class Tee {
    constructor(){
        this.terminal = sys.stdout;
        this.log = null;
        this.filename = null;
    }

    redirect(filename = null){
        if (self.log !== null){
            this.log.close();
        }

        this.filename = filename;

        if (filename === null){
            this.log = null;
        } else{
            this.log = open(filename, 'w');
        }
    }

    write(message){
        this.terminal.write(message);

        if (this.log !== null){
            this.log.write(message);
        }
    }

    flush(){
        this.terminal.flush();
    }
}
module.exports = Tee;