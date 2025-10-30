for i in {1..20}; do
  java Assignment2cli 127.0.0.1 5000 file$i.txt &
done
wait
